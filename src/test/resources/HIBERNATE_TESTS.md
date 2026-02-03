# Hibernate RAG Test Suite

## Overview

This test suite validates that Hibernate ORM documentation is correctly ingested, embedded, and retrieved through the RAG system. It complements the existing Quarkus golden set tests.

## Test Files

### rag-eval-hibernate.json
**Location:** `src/test/resources/rag-eval-hibernate.json`

**Contents:** 25 test cases covering Hibernate-specific topics:
- Entity mapping and relationships
- Fetching strategies (LAZY vs EAGER)
- Caching (second-level cache)
- Connection pooling
- Query APIs (HQL, Criteria, Native SQL)
- Transaction management
- Cascade types
- Inheritance mapping
- Composite keys and embeddables
- Generated values
- Batch processing
- Advanced features (Formula, Where, Filters, Envers)
- Locking (optimistic and pessimistic)
- Session management
- AttributeConverter

### Test Class: RagHibernateGoldenSetTest.java
**Location:** `src/test/java/org/chappiebot/rag/RagHibernateGoldenSetTest.java`

**Features:**
- Executes all Hibernate test cases
- Uses combined Docker image (`chappie-ingestion-all:3.15.0`)
- Validates library filtering works correctly
- Includes sanity checks for Hibernate doc presence
- Detailed output with scores and sources

## Test Format

Each test case follows this structure:

```json
{
  "id": "unique-test-identifier",
  "query": "The question to ask",
  "assertions": {
    "anyRepoPathContains": ["keyword1", "keyword2"],
    "minScoreAtRankLe": {
      "rank": 10,   // Top 10 results
      "score": 0.82 // Minimum score threshold
    }
  }
}
```

### Assertion Types

**anyRepoPathContains:**
- Case-insensitive substring match
- For Hibernate docs, checks both `repo_path` and `url` fields
- Example: `["hibernate"]` matches docs from Hibernate documentation

**anyRepoPathEndsWith:**
- Exact suffix match on `repo_path`
- Used for Quarkus docs (e.g., `["datasource.adoc"]`)
- Not typically used for Hibernate docs

**minScoreAtRankLe:**
- Validates score quality within rank threshold
- `rank`: Position within top N results (1-based)
- `score`: Minimum similarity score (0.0-1.0)
- Example: `{"rank": 10, "score": 0.82}` = At least one result in top 10 has score >= 0.82

**minMatches:**
- Minimum number of total results required
- Optional, defaults to > 0 results

## Score Interpretation

| Score Range | Quality |
|-------------|---------|
| 0.95+ | Exceptional (near-identical) |
| 0.90-0.95 | Excellent (highly relevant) |
| 0.85-0.90 | Very good (clearly relevant) |
| 0.82-0.85 | Good (relevant, acceptable) |
| 0.75-0.82 | Moderate (may be relevant) |
| < 0.75 | Weak (likely not relevant) |

**Hibernate test threshold:** 0.82 (good quality baseline)

## Running Tests

### Run Hibernate Golden Set
```bash
cd chappie-server
./mvnw test -Dtest=RagHibernateGoldenSetTest
```

### Run with Different Image
```bash
./mvnw test -Dtest=RagHibernateGoldenSetTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-all:3.16.0
```

### Run All RAG Tests (Quarkus + Hibernate)
```bash
./mvnw test -Dtest=RagGoldenSetTest,RagHibernateGoldenSetTest
```

### Run with Debug Output
```bash
./mvnw test -Dtest=RagHibernateGoldenSetTest -X
```

## Test Output Example

```
=== HIBERNATE RAG GOLDEN SET TESTS ===
Total test cases: 25
Docker image: ghcr.io/quarkusio/chappie-ingestion-all:3.15.0
==========================================

--- Test: hibernate-entity-mapping ---
Query: How do I map a JPA entity in Hibernate?
Top 10 results:
   1. [0.8845] hibernate-orm - Hibernate User Guide (https://docs.jboss.org/...)
   2. [0.8523] hibernate-orm - Introduction to Hibernate ORM
   3. [0.8234] quarkus - hibernate-orm
   4. [0.8012] quarkus - hibernate-orm-panache
   ...

✓ PASS: hibernate-entity-mapping
✓ PASS: hibernate-relationships
✓ PASS: hibernate-lazy-eager
...

=== TEST SUMMARY ===
Passed: 25 / 25
Failed: 0 / 25
Success Rate: 100.0%
==========================================
```

## Docker Image Requirements

The tests require the **combined Docker image** that includes both Quarkus and Hibernate documentation:

```
ghcr.io/quarkusio/chappie-ingestion-all:3.15.0
```

### Building the Combined Image

```bash
cd ../chappie-docling-rag

java -jar target/quarkus-app/quarkus-run.jar bake-image \
  --quarkus-version=3.15.0 \
  --doc-source=ALL \
  --semantic
```

### Image Contents
- **Quarkus:** ~250 guides
- **Hibernate:** 2-3 comprehensive documents (Introduction, User Guide)
- **Total size:** ~700-800 MB

## Test Infrastructure

### Key Components

**RetrievalProvider:**
- Executes vector similarity search
- Applies library filtering (`library IN ('quarkus', 'hibernate-orm')`)
- Performs metadata-based score boosting
- Returns ranked results

**RagImageDbResource:**
- Testcontainers lifecycle manager
- Starts pgvector Docker container
- Configures datasource connection
- Injects configuration into tests

**RagEvalCase:**
- Test case data model
- Parses JSON test definitions
- Validates assertions

## Metadata Fields

Hibernate documents include these metadata fields:

| Field | Example | Purpose |
|-------|---------|---------|
| `library` | `"hibernate-orm"` | Library identification |
| `library_version` | `"6.4.4.Final"` | Hibernate version |
| `quarkus_version` | `"3.15.0"` | Related Quarkus version |
| `quarkus_extensions` | `"quarkus-hibernate-orm,..."` | Related extensions |
| `title` | `"Hibernate User Guide"` | Document title |
| `topics` | `"mapping, persistence, queries"` | Topic keywords |
| `categories` | `"orm,persistence,database"` | Categories |
| `url` | `"https://docs.jboss.org/..."` | Source URL |

## Adding New Tests

### 1. Add to JSON File

```json
{
  "id": "your-test-id",
  "query": "Your Hibernate question?",
  "assertions": {
    "anyRepoPathContains": ["hibernate"],
    "minScoreAtRankLe": { "rank": 10, "score": 0.82 }
  }
}
```

### 2. Run Tests

```bash
./mvnw test -Dtest=RagHibernateGoldenSetTest
```

### 3. Adjust Assertions

If tests fail, check:
- Is the query too vague?
- Is the score threshold too high?
- Are the keywords appropriate?
- Is the Hibernate documentation available?

### 4. Baseline Capture

```bash
./mvnw test -Dtest=RagBaselineTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-all:3.15.0
```

## Troubleshooting

### Issue: No Hibernate docs found

**Symptoms:**
```
No Hibernate documentation found! Ensure the combined Docker image...
```

**Solution:**
1. Verify image exists: `docker images | grep chappie-ingestion-all`
2. Build combined image: `bake-image --doc-source=ALL`
3. Check image version matches test configuration

### Issue: Low scores for Hibernate queries

**Symptoms:** All tests fail with scores < 0.82

**Possible causes:**
1. Hibernate docs not properly embedded
2. Library metadata missing
3. Query too generic (try more specific keywords)

**Solution:**
1. Verify Hibernate metadata: Run `verify_hibernate_docs_are_present()`
2. Check embeddings: Query similar to docs should score > 0.85
3. Adjust score thresholds if necessary

### Issue: Tests pass for Quarkus but fail for Hibernate

**Symptoms:** Hibernate queries return Quarkus docs instead

**Solution:**
1. Verify library filtering: Run `verify_library_filtering()`
2. Check `chappie.rag.libraries` configuration
3. Ensure library metadata is set correctly in Hibernate docs

## Test Coverage

### Current Coverage (25 tests)

**Core Concepts:**
- Entity mapping
- Relationships (OneToMany, ManyToOne, etc.)
- Fetching strategies
- Caching

**Querying:**
- HQL queries
- Criteria API
- Native SQL
- Named queries

**Advanced Features:**
- Inheritance mapping
- Composite keys
- Embeddables
- Generated values
- Batch processing
- Converters

**Data Management:**
- Transaction management
- Cascade types
- Locking (optimistic/pessimistic)
- Session management

**Specialized Features:**
- @Formula
- @Where (soft deletes)
- Filters
- Envers (auditing)

### Future Coverage

Potential additions:
- Hibernate Validator integration
- Custom types
- Event listeners
- Interceptors
- Multi-tenancy
- Database schema generation
- Performance tuning
- N+1 query problems
- Projection queries
- EntityGraph

## Integration with Existing Tests

### Relationship to Quarkus Tests

**Quarkus Golden Set (`rag-eval.json`):**
- 36 test cases
- Quarkus-specific topics
- Uses `chappie-ingestion-quarkus:3.31.1` image
- 100% pass rate

**Hibernate Golden Set (`rag-eval-hibernate.json`):**
- 25 test cases
- Hibernate-specific topics
- Uses `chappie-ingestion-all:3.15.0` image
- Target: 100% pass rate

**Combined Testing:**
Both test suites can run together to validate the complete multi-source documentation system.

## Success Criteria

✅ All 25 Hibernate tests pass
✅ Scores meet minimum thresholds (0.82+)
✅ Library filtering works correctly
✅ Hibernate docs clearly identified in results
✅ No false positives (Quarkus docs for Hibernate queries)

## CI/CD Integration

### GitHub Actions Example

```yaml
- name: Run RAG Tests
  run: |
    # Pull combined image
    docker pull ghcr.io/quarkusio/chappie-ingestion-all:3.15.0

    # Run Quarkus tests
    ./mvnw test -Dtest=RagGoldenSetTest

    # Run Hibernate tests
    ./mvnw test -Dtest=RagHibernateGoldenSetTest

    # Run comparison (if baseline exists)
    ./mvnw test -Dtest=RagComparisonTest
```

### Required Environment

- Docker available
- Image pulled or built locally
- Maven 3.8+
- Java 17+

## Performance Expectations

**Test Execution Time:**
- Single test: ~500ms (includes vector search + validation)
- Full suite (25 tests): ~12-15 seconds
- Container startup: ~10-15 seconds (one-time)

**Resource Usage:**
- Memory: ~2GB (Docker container + JVM)
- Disk: ~1GB (Docker image)

## Maintenance

### When to Update Tests

1. **New Hibernate version** - Add version-specific tests
2. **Documentation changes** - Verify existing tests still pass
3. **New features** - Add tests for newly documented features
4. **Failing tests** - Investigate and fix or update assertions

### Baseline Management

Capture new baseline after:
- Documentation updates
- Embedding model changes
- Chunking strategy changes
- Metadata boosting adjustments

```bash
./mvnw test -Dtest=RagBaselineTest
```
