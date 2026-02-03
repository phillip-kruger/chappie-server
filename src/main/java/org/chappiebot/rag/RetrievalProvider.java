package org.chappiebot.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.chappiebot.store.StoreManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RetrievalProvider {

    @Inject
    StoreManager storeManager;

    EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    @ConfigProperty(name = "chappie.rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "chappie.rag.results.max", defaultValue = "4")
    int ragMaxResults;

    @ConfigProperty(name = "chappie.rag.score.min", defaultValue = "0.82")
    double ragMinScore;

    @ConfigProperty(name = "chappie.rag.libraries", defaultValue = "quarkus")
    String activeLibraries;

    @PostConstruct
    public void init() {
        if (ragEnabled) {
            loadEmbeddingModel();
            loadVectorStore();
            if (embeddingStore == null) {
                Log.warn("RAG enabled but no embedding store available; disabling RAG for this run");
                ragEnabled = false;
            }
        }
    }

    public int getRagMaxResults() {
        return ragMaxResults;
    }

    public String getActiveLibraries() {
        return activeLibraries;
    }

    private void loadVectorStore() {
        this.embeddingStore = storeManager.getStore().orElse(null);
    }

    private void loadEmbeddingModel() {
        embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    // TODO do we need to pad the extensions with commas to avoid partial matches?
    private Filter extensionFilter(String extension) {
        return new ContainsString("extensions_csv_padded", extension);
    }

    /**
     * Creates a filter for library-based filtering.
     * Supports filtering by single library or comma-separated list.
     *
     * @param libraries Comma-separated list of libraries (e.g., "quarkus,hibernate-orm")
     * @return Filter that matches documents from any of the specified libraries
     */
    private Filter libraryFilter(String libraries) {
        if (libraries == null || libraries.trim().isEmpty()) {
            return null;
        }

        String[] libs = libraries.split(",");
        if (libs.length == 0) {
            return null;
        }

        // Trim whitespace
        for (int i = 0; i < libs.length; i++) {
            libs[i] = libs[i].trim();
        }

        // Single library - simple equality check
        if (libs.length == 1) {
            return new IsEqualTo("library", libs[0]);
        }

        // Multiple libraries - chain OR conditions
        // Or takes exactly 2 arguments, so we chain them: (a OR b) OR c OR d...
        Filter result = new IsEqualTo("library", libs[0]);
        for (int i = 1; i < libs.length; i++) {
            result = new Or(result, new IsEqualTo("library", libs[i]));
        }
        return result;
    }

    /**
     * Combines multiple filters using AND logic.
     *
     * @param filters Filters to combine (nulls are ignored)
     * @return Combined filter, or null if no valid filters
     */
    private Filter combineFilters(Filter... filters) {
        List<Filter> validFilters = new java.util.ArrayList<>();
        for (Filter filter : filters) {
            if (filter != null) {
                validFilters.add(filter);
            }
        }

        if (validFilters.isEmpty()) {
            return null;
        }
        if (validFilters.size() == 1) {
            return validFilters.get(0);
        }

        // Chain AND conditions: (a AND b) AND c AND d...
        Filter result = validFilters.get(0);
        for (int i = 1; i < validFilters.size(); i++) {
            result = new And(result, validFilters.get(i));
        }
        return result;
    }

    public List<SearchMatch> search(String queryMessage, int maxResults, String restrictToExtension) {
        return search(queryMessage, maxResults, restrictToExtension, null, true);
    }

    public List<SearchMatch> search(String queryMessage, int maxResults, String restrictToExtension, boolean useMetadataBoost) {
        return search(queryMessage, maxResults, restrictToExtension, null, useMetadataBoost);
    }

    /**
     * Search with library and extension filtering.
     *
     * @param queryMessage The search query
     * @param maxResults Maximum number of results to return
     * @param restrictToExtension Optional extension filter (e.g., "quarkus-hibernate-orm")
     * @param restrictToLibraries Optional library filter (e.g., "quarkus,hibernate-orm"). If null, uses configured default.
     * @param useMetadataBoost Whether to apply metadata-based score boosting
     * @return List of search matches
     */
    public List<SearchMatch> search(String queryMessage, int maxResults, String restrictToExtension,
                                     String restrictToLibraries, boolean useMetadataBoost) {
        Embedding embeddedQuery = embeddingModel.embed(queryMessage).content();

        // Fetch more results if using metadata boost, so we can rerank
        int fetchCount = useMetadataBoost ? Math.max(maxResults * 5, 50) : maxResults;

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddedQuery)
                .maxResults(fetchCount)
                .minScore(0.0);

        // Build combined filter (library AND extension if both specified)
        Filter extensionFilt = null;
        Filter libraryFilt = null;

        if (restrictToExtension != null) {
            Log.info("Restricting search to extension: " + restrictToExtension);
            extensionFilt = extensionFilter(restrictToExtension);
        }

        // Use provided libraries or fall back to configured default
        String librariesToUse = (restrictToLibraries != null) ? restrictToLibraries : activeLibraries;
        if (librariesToUse != null && !librariesToUse.trim().isEmpty()) {
            Log.infof("Restricting search to libraries: %s", librariesToUse);
            libraryFilt = libraryFilter(librariesToUse);
        }

        Filter combinedFilter = combineFilters(libraryFilt, extensionFilt);
        if (combinedFilter != null) {
            requestBuilder.filter(combinedFilter);
        }

        EmbeddingSearchRequest searchRequest = requestBuilder.build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        List<SearchMatch> matches = searchResult.matches().stream()
                .map(RetrievalProvider::extractContent)
                .collect(Collectors.toList());

        // Apply metadata boosting if enabled
        if (useMetadataBoost) {
            matches = applyMetadataBoost(matches, queryMessage);
        }

        // Return only requested number of results
        return matches.stream().limit(maxResults).collect(Collectors.toList());
    }

    /**
     * Boosts search results when document metadata (title, repo_path) matches query keywords.
     * This implements hybrid search combining semantic similarity with keyword matching.
     */
    private List<SearchMatch> applyMetadataBoost(List<SearchMatch> matches, String query) {
        // Extract potential keywords from query
        String[] words = query.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", " ")  // Remove punctuation except hyphens
                .split("\\s+");

        // Separate direct keywords from synonyms
        List<String> directKeywords = new java.util.ArrayList<>();
        List<String> synonymKeywords = new java.util.ArrayList<>();

        for (String word : words) {
            // Include words > 3 chars, or all-caps acronyms (CDI, JWT, etc.)
            if ((word.length() > 3 || word.matches("[a-z]{2,3}")) && !isStopWord(word)) {
                directKeywords.add(word);
                // Add synonyms/related terms (but track them separately)
                synonymKeywords.addAll(getSynonyms(word));
            }
        }

        if (directKeywords.isEmpty() && synonymKeywords.isEmpty()) {
            return matches;  // No keywords to boost on
        }

        Log.debugf("Metadata boost - direct keywords: %s, synonyms: %s", directKeywords, synonymKeywords);

        // Boost scores for metadata matches
        List<SearchMatch> boosted = new java.util.ArrayList<>();
        for (SearchMatch match : matches) {
            double originalScore = match.score();
            double boostedScore = originalScore;

            String title = String.valueOf(match.metadata().getOrDefault("title", "")).toLowerCase();
            String repoPath = String.valueOf(match.metadata().getOrDefault("repo_path", "")).toLowerCase();
            String docKeywords = String.valueOf(match.metadata().getOrDefault("keywords", "")).toLowerCase();
            String docTopics = String.valueOf(match.metadata().getOrDefault("topics", "")).toLowerCase();

            // Check for DIRECT keyword matches (higher boost)
            for (String keyword : directKeywords) {
                if (title.contains(keyword)) {
                    boostedScore += 0.15;  // Significant boost for direct title match
                    Log.debugf("Metadata boost: title '%s' matches keyword '%s' (+0.15)", title, keyword);
                }
                if (repoPath.contains(keyword)) {
                    boostedScore += 0.10;  // Moderate boost for direct repo_path match
                    Log.debugf("Metadata boost: repo_path '%s' matches keyword '%s' (+0.10)", repoPath, keyword);
                }
                if (docKeywords.contains(keyword)) {
                    boostedScore += 0.20;  // Strong boost for keywords match (most specific!)
                    Log.debugf("Metadata boost: keywords '%s' matches '%s' (+0.20)", docKeywords, keyword);
                }
                if (docTopics.contains(keyword)) {
                    boostedScore += 0.25;  // STRONGEST boost for topics match (from AsciiDoc metadata!)
                    Log.debugf("Metadata boost: topics '%s' matches '%s' (+0.25)", docTopics, keyword);
                }
            }

            // Check for SYNONYM matches (moderate boost)
            for (String synonym : synonymKeywords) {
                if (title.contains(synonym)) {
                    boostedScore += 0.12;  // Moderate boost for synonym title match
                    Log.debugf("Metadata boost: title '%s' matches synonym '%s' (+0.12)", title, synonym);
                }
                if (repoPath.contains(synonym)) {
                    boostedScore += 0.08;  // Moderate boost for synonym repo_path match
                    Log.debugf("Metadata boost: repo_path '%s' matches synonym '%s' (+0.08)", repoPath, synonym);
                }
                if (docKeywords.contains(synonym)) {
                    boostedScore += 0.15;  // Good boost for synonym in keywords
                    Log.debugf("Metadata boost: keywords '%s' matches synonym '%s' (+0.15)", docKeywords, synonym);
                }
                if (docTopics.contains(synonym)) {
                    boostedScore += 0.20;  // Strong boost for synonym in topics
                    Log.debugf("Metadata boost: topics '%s' matches synonym '%s' (+0.20)", docTopics, synonym);
                }
            }

            // Create new SearchMatch with boosted score
            if (boostedScore > originalScore) {
                boosted.add(new SearchMatch(match.text(), match.source(), boostedScore, match.metadata()));
            } else {
                boosted.add(match);
            }
        }

        // Re-sort by boosted scores
        boosted.sort((a, b) -> Double.compare(b.score(), a.score()));

        return boosted;
    }

    private boolean isStopWord(String word) {
        // Common English stop words that shouldn't be used for boosting
        return List.of("this", "that", "with", "from", "have", "does", "what", "when",
                      "where", "which", "their", "about", "would", "there", "these",
                      "using", "quarkus", "guide").contains(word);
    }

    /**
     * Returns synonyms and related terms for common technical keywords.
     * Helps match queries like "startup" with documents about "lifecycle".
     */
    private List<String> getSynonyms(String word) {
        return switch (word) {
            case "startup", "start" -> List.of("lifecycle", "init", "initialization");
            case "lifecycle" -> List.of("startup", "init");
            // Note: Don't map "inject" to "cdi" as it causes false positives (config injection vs bean injection)
            case "injection" -> List.of("cdi", "dependency");  // Only "injection", not "inject"
            case "cdi" -> List.of("injection", "dependency");
            case "validation", "validate" -> List.of("hibernate-validator", "validator");
            case "validator", "hibernate-validator" -> List.of("validation", "validate");
            case "mode" -> List.of("dev-mode", "continuous-testing");
            case "cors" -> List.of("cross-origin");
            default -> List.of();
        };
    }

    private static SearchMatch extractContent(EmbeddingMatch<TextSegment> embeddingMatch) {
        Map<String, Object> metadata = embeddingMatch.embedded().metadata().toMap();
        // Remove the actual embedding vector from metadata to reduce payload size
        metadata.remove("embedding");
        return new SearchMatch(embeddingMatch.embedded().text(), embeddingMatch.embeddingId(), embeddingMatch.score(),
                metadata);
    }

    public RetrievalAugmentor getRetrievalAugmentor(Function<Query, Filter> filterFunction) {
        if (ragEnabled && embeddingModel != null) {

            var retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(ragMaxResults)
                    .minScore(ragMinScore)
                    .dynamicFilter(filterFunction)
                    .build();

            // TODO: Maybe skip RAG if the user message word count is less than 3 or something ?
        
            ContentInjector contentInjector = new ContentInjector(){
                @Override
                public ChatMessage inject(List<Content> contents, ChatMessage cm) {
                    if (cm == null) {
                        return UserMessage.from("");
                    }

                    if (cm.type() != ChatMessageType.USER) {
                        return cm;
                    }

                    if (contents == null || contents.isEmpty()) {
                        return cm;
                    }

                    String contextBlock = contents.stream()
                        .map(c -> {

                            Object score = c.metadata().getOrDefault(ContentMetadata.SCORE, 0);
                            // TODO: Can we surface the Score somehow ?
                            String t = c.textSegment().text();
                            if (t == null) return "";
                            if (t.length() > 1400) t = t.substring(0, 1400) + " …"; // TODO: Make 1400 a input option
                            return t;
                        })
                        .filter(s -> !s.isBlank())
                        .limit(ragMaxResults)
                        .collect(Collectors.joining("\n---\n"));

                    if (contextBlock.isBlank()) {
                        return cm;
                    }

                    String preface = """
                        [RAG CONTEXT]
                        Use this as a guide only. It may be incomplete or irrelevant.
                        If it conflicts with known facts or user intent, explain and prefer correctness.
                        If irrelevant, say so and answer without it.

                        <context>
                        %s
                        </context>
                        [/RAG CONTEXT]
                        """.formatted(contextBlock);

                    String userText = ((UserMessage) cm).singleText();
                    String combined = (userText == null || userText.isBlank())
                        ? preface
                        : userText + "\n\n" + preface;

                    return UserMessage.from(combined);
                }
            };

            Log.infof("CHAPPiE RAG is enabled with %d max results and min score %.2f", ragMaxResults, ragMinScore);
            
            return DefaultRetrievalAugmentor.builder()
                .contentInjector(contentInjector)
                .contentRetriever(retriever)
                .build();
        }
        return null;
    }
}
