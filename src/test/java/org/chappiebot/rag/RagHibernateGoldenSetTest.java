package org.chappiebot.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.chappiebot.search.SearchMatch;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Tests for Hibernate-specific RAG golden set.
 *
 * This test suite validates that Hibernate documentation is correctly ingested,
 * embedded, and retrieved through the RAG system. It tests Hibernate-specific
 * questions that should return Hibernate documentation rather than generic Quarkus docs.
 *
 * The tests use the combined Docker image (chappie-ingestion-all) which includes
 * both Quarkus and Hibernate documentation.
 *
 * Expected behavior:
 * - Hibernate-specific queries should return Hibernate documentation
 * - Library filtering should be working (library='hibernate-orm')
 * - Scores should meet minimum thresholds (0.82+)
 *
 * Test data: src/test/resources/rag-eval-hibernate.json
 */
@QuarkusTest
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        // Use combined image with Quarkus + Hibernate docs
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-all:3.15.0"),
        @ResourceArg(name = "dim", value = "384")
    }
)
public class RagHibernateGoldenSetTest {

    private static final String TEST_FILE = "/rag-eval-hibernate.json";
    private static final int DEFAULT_MAX_RESULTS = 10;

    @Inject
    RetrievalProvider retrievalProvider;

    @Test
    public void hibernate_golden_set_should_pass() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<RagEvalCase> cases;

        try (InputStream is = getClass().getResourceAsStream(TEST_FILE)) {
            assertNotNull(is, "Test file not found: " + TEST_FILE);
            cases = mapper.readValue(is,
                    mapper.getTypeFactory().constructCollectionType(List.class, RagEvalCase.class));
        }

        assertFalse(cases.isEmpty(), "No test cases found in " + TEST_FILE);

        int passed = 0;
        int failed = 0;
        StringBuilder failures = new StringBuilder();

        System.out.println("\n=== HIBERNATE RAG GOLDEN SET TESTS ===");
        System.out.println("Total test cases: " + cases.size());
        System.out.println("Docker image: ghcr.io/quarkusio/chappie-ingestion-all:3.15.0");
        System.out.println("==========================================\n");

        for (RagEvalCase testCase : cases) {
            try {
                executeTest(testCase);
                passed++;
                System.out.println("✓ PASS: " + testCase.id);
            } catch (AssertionError e) {
                failed++;
                failures.append("\n❌ FAIL: ").append(testCase.id).append("\n");
                failures.append("   Query: ").append(testCase.query).append("\n");
                failures.append("   Error: ").append(e.getMessage()).append("\n");
                System.err.println("❌ FAIL: " + testCase.id);
                System.err.println("   " + e.getMessage());
            }
        }

        System.out.println("\n=== TEST SUMMARY ===");
        System.out.println("Passed: " + passed + " / " + cases.size());
        System.out.println("Failed: " + failed + " / " + cases.size());
        System.out.println("Success Rate: " + String.format("%.1f%%", (passed * 100.0 / cases.size())));
        System.out.println("==========================================\n");

        if (failed > 0) {
            System.err.println("\n=== FAILURES ===");
            System.err.println(failures.toString());
            fail(String.format("%d/%d Hibernate golden set tests failed", failed, cases.size()));
        }
    }

    private void executeTest(RagEvalCase testCase) {
        int maxResults = testCase.maxResults != null ? testCase.maxResults : DEFAULT_MAX_RESULTS;

        // Execute search with library filtering to Hibernate
        // Note: We could filter to "hibernate-orm" only, but we'll search both and verify
        // Hibernate docs are returned (more realistic scenario)
        List<SearchMatch> results = retrievalProvider.search(
                testCase.query,
                maxResults,
                testCase.restrictToExtension,
                "quarkus,hibernate-orm",  // Search both libraries
                true  // Use metadata boosting
        );

        // Print top results for debugging
        System.out.println("\n--- Test: " + testCase.id + " ---");
        System.out.println("Query: " + testCase.query);
        System.out.println("Top " + Math.min(10, results.size()) + " results:");
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            SearchMatch match = results.get(i);
            String library = (String) match.metadata().getOrDefault("library", "unknown");
            String title = (String) match.metadata().getOrDefault("title", "unknown");
            String url = (String) match.metadata().getOrDefault("url", "");
            System.out.printf("  %2d. [%.4f] %s - %s%s\n",
                    i + 1, match.score(), library, title,
                    url.isEmpty() ? "" : " (" + url + ")");
        }

        // Validate assertions
        RagEvalCase.Assertions assertions = testCase.assertions;
        if (assertions == null) {
            return; // No assertions to check
        }

        // Check minimum matches
        if (assertions.minMatches != null) {
            assertTrue(results.size() >= assertions.minMatches,
                    String.format("Expected at least %d matches, got %d",
                            assertions.minMatches, results.size()));
        } else {
            assertFalse(results.isEmpty(), "No results returned for query: " + testCase.query);
        }

        // Extract all repo paths and URLs for validation
        List<String> repoPaths = results.stream()
                .map(m -> (String) m.metadata().getOrDefault("repo_path", ""))
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> urls = results.stream()
                .map(m -> (String) m.metadata().getOrDefault("url", ""))
                .filter(s -> !s.isEmpty())
                .toList();

        // Check anyRepoPathEndsWith (for Quarkus docs)
        if (assertions.anyRepoPathEndsWith != null && !assertions.anyRepoPathEndsWith.isEmpty()) {
            boolean found = repoPaths.stream()
                    .anyMatch(path -> assertions.anyRepoPathEndsWith.stream()
                            .anyMatch(path::endsWith));
            assertTrue(found,
                    "No repo_path ends with any of: " + assertions.anyRepoPathEndsWith +
                            "\nActual paths: " + repoPaths);
        }

        // Check anyRepoPathContains (works for both Quarkus and Hibernate)
        if (assertions.anyRepoPathContains != null && !assertions.anyRepoPathContains.isEmpty()) {
            boolean foundInPath = repoPaths.stream()
                    .anyMatch(path -> assertions.anyRepoPathContains.stream()
                            .anyMatch(substring -> path.toLowerCase().contains(substring.toLowerCase())));

            // Also check URLs for Hibernate docs (which don't have repo_path)
            boolean foundInUrl = urls.stream()
                    .anyMatch(url -> assertions.anyRepoPathContains.stream()
                            .anyMatch(substring -> url.toLowerCase().contains(substring.toLowerCase())));

            assertTrue(foundInPath || foundInUrl,
                    "No repo_path or URL contains any of: " + assertions.anyRepoPathContains +
                            "\nActual paths: " + repoPaths +
                            "\nActual URLs: " + urls);
        }

        // Check minScoreAtRankLe
        if (assertions.minScoreAtRankLe != null) {
            int rank = assertions.minScoreAtRankLe.rank;
            double minScore = assertions.minScoreAtRankLe.score;

            assertTrue(results.size() >= rank,
                    String.format("Not enough results (need %d, got %d)", rank, results.size()));

            boolean found = false;
            for (int i = 0; i < Math.min(rank, results.size()); i++) {
                if (results.get(i).score() >= minScore) {
                    found = true;
                    break;
                }
            }

            assertTrue(found,
                    String.format("No result in top %d has score >= %.2f. Top scores: %s",
                            rank, minScore,
                            results.stream()
                                    .limit(rank)
                                    .map(m -> String.format("%.4f", m.score()))
                                    .toList()));
        }
    }

    /**
     * Verify that Hibernate docs are actually present in the database.
     * This is a sanity check to ensure the combined image was loaded correctly.
     */
    @Test
    public void verify_hibernate_docs_are_present() {
        List<SearchMatch> results = retrievalProvider.search(
                "Hibernate ORM",
                10,
                null,
                "hibernate-orm",  // Filter to Hibernate only
                false  // No metadata boosting for this check
        );

        assertFalse(results.isEmpty(),
                "No Hibernate documentation found! Ensure the combined Docker image " +
                        "(chappie-ingestion-all:3.15.0) is loaded correctly.");

        // Verify at least one result has library=hibernate-orm
        boolean hasHibernate = results.stream()
                .anyMatch(m -> "hibernate-orm".equals(m.metadata().get("library")));

        assertTrue(hasHibernate,
                "No results have library=hibernate-orm. Available libraries: " +
                        results.stream()
                                .map(m -> (String) m.metadata().get("library"))
                                .distinct()
                                .toList());

        System.out.println("\n✓ Hibernate documentation verified in database");
        System.out.println("  Found " + results.size() + " Hibernate docs");
        System.out.println("  Sample: " + results.get(0).metadata().get("title"));
    }

    /**
     * Verify that library filtering works correctly.
     * Tests that we can filter to only Hibernate docs vs only Quarkus docs.
     */
    @Test
    public void verify_library_filtering() {
        String query = "How do I configure database connections?";

        // Search Hibernate only
        List<SearchMatch> hibernateResults = retrievalProvider.search(
                query, 5, null, "hibernate-orm", false);

        // Search Quarkus only
        List<SearchMatch> quarkusResults = retrievalProvider.search(
                query, 5, null, "quarkus", false);

        // Verify library metadata
        if (!hibernateResults.isEmpty()) {
            String lib = (String) hibernateResults.get(0).metadata().get("library");
            assertEquals("hibernate-orm", lib,
                    "Expected Hibernate result but got library: " + lib);
        }

        if (!quarkusResults.isEmpty()) {
            String lib = (String) quarkusResults.get(0).metadata().get("library");
            assertEquals("quarkus", lib,
                    "Expected Quarkus result but got library: " + lib);
        }

        System.out.println("\n✓ Library filtering verified");
        System.out.println("  Hibernate results: " + hibernateResults.size());
        System.out.println("  Quarkus results: " + quarkusResults.size());
    }
}
