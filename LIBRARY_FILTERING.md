# Library-Based RAG Filtering

## Overview

chappie-server now supports filtering RAG (Retrieval-Augmented Generation) results based on documentation libraries. This enables intelligent filtering of documentation based on which libraries are actually used in the application.

## Features

### 1. Library Metadata

All documents in the vector store now include library identification metadata:

```json
{
  "library": "hibernate-orm",
  "library_version": "6.4.4.Final",
  "quarkus_version": "3.15.0",
  "quarkus_extensions": "quarkus-hibernate-orm,quarkus-hibernate-orm-panache"
}
```

### 2. Configurable Default Libraries

Configure which libraries are active by default:

```properties
# Default: only Quarkus documentation
chappie.rag.libraries=quarkus

# Include Hibernate documentation
chappie.rag.libraries=quarkus,hibernate-orm

# Multiple libraries
chappie.rag.libraries=quarkus,hibernate-orm,smallrye-config
```

### 3. Runtime Library Filtering

Filter results at query time using request context variables:

```java
// In ChappieAssistant or calling code
Map<String, String> variables = new HashMap<>();
variables.put("libraries", "quarkus,hibernate-orm");
ragRequestContext.setVariables(variables);

// Query will only return results from Quarkus and Hibernate docs
assistant.assist(userMessage);
```

### 4. API Search Endpoint

The `/api/search` endpoint supports library filtering:

```bash
# Search only Quarkus docs
curl -X POST http://localhost:4315/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "queryMessage": "How do I configure datasources?",
    "libraries": "quarkus"
  }'

# Search Quarkus and Hibernate docs
curl -X POST http://localhost:4315/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "queryMessage": "How do I map JPA entities?",
    "libraries": "quarkus,hibernate-orm"
  }'

# Search with both library and extension filters
curl -X POST http://localhost:4315/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "queryMessage": "How do I use Panache?",
    "libraries": "quarkus,hibernate-orm",
    "extension": "quarkus-hibernate-orm-panache"
  }'
```

## Configuration Properties

### chappie.rag.libraries

**Description:** Comma-separated list of active libraries for RAG filtering.

**Default:** `quarkus`

**Examples:**
```properties
# Quarkus only (default)
chappie.rag.libraries=quarkus

# Quarkus + Hibernate
chappie.rag.libraries=quarkus,hibernate-orm

# Multiple libraries
chappie.rag.libraries=quarkus,hibernate-orm,smallrye-config,smallrye-reactive-messaging
```

**Valid library names:**
- `quarkus` - Quarkus guides
- `hibernate-orm` - Hibernate ORM documentation
- `smallrye-config` - SmallRye Config documentation
- `smallrye-reactive-messaging` - SmallRye Reactive Messaging
- `smallrye-jwt` - SmallRye JWT
- `jakarta-ee` - Jakarta EE specifications
- `microprofile` - MicroProfile specifications

## How Filtering Works

### 1. Default Behavior

If no library filter is specified, the configured default (`chappie.rag.libraries`) is used:

```java
// Uses configured default libraries
List<SearchMatch> results = retrievalProvider.search(query, maxResults, null);
```

### 2. Override at Query Time

Specify libraries in the SearchRequest or context variables:

```java
// Override with specific libraries
List<SearchMatch> results = retrievalProvider.search(
    query,
    maxResults,
    null,  // no extension filter
    "quarkus,hibernate-orm",  // library filter
    true   // use metadata boost
);
```

### 3. Combining Library and Extension Filters

Library and extension filters are combined with AND logic:

```java
// Results must match BOTH:
// - library IN (quarkus, hibernate-orm)
// - AND extensions contains "quarkus-hibernate-orm"
List<SearchMatch> results = retrievalProvider.search(
    query,
    maxResults,
    "quarkus-hibernate-orm",     // extension filter
    "quarkus,hibernate-orm",      // library filter
    true
);
```

### 4. Dynamic Filtering via Context Variables

The most powerful approach - set variables in RagRequestContext:

```java
Map<String, String> variables = new HashMap<>();

// Detect active libraries from dependencies
if (hasHibernateDependency()) {
    variables.put("libraries", "quarkus,hibernate-orm");
} else {
    variables.put("libraries", "quarkus");
}

// Optionally filter by extension
if (specificExtension != null) {
    variables.put("extension", specificExtension);
}

ragRequestContext.setVariables(variables);
```

## Integration with quarkus-chappie Extension

The quarkus-chappie extension can automatically detect dependencies and configure library filtering:

```java
// In ChappieProcessor.java (build-time)
@BuildStep
void configureLibraryFiltering(
    CurateOutcomeBuildItem curateOutcome,
    BuildProducer<ConfigPropertyBuildItem> config) {

    Set<String> libraries = new HashSet<>();
    libraries.add("quarkus"); // Always include Quarkus

    // Detect Hibernate
    if (hasDependency(curateOutcome, "hibernate-orm")) {
        libraries.add("hibernate-orm");
    }

    // Detect SmallRye projects
    if (hasDependency(curateOutcome, "smallrye-config")) {
        libraries.add("smallrye-config");
    }

    // Pass to chappie-server
    config.produce(new ConfigPropertyBuildItem(
        "chappie.rag.libraries",
        String.join(",", libraries)
    ));
}
```

## Performance Considerations

### Filter Execution

Library filtering happens **before** vector similarity search:

1. PostgreSQL filters rows by `library` metadata field
2. Vector search executes only on filtered subset
3. Minimal performance overhead (< 5ms)

### Index Optimization

The `library` metadata field should be indexed for optimal performance:

```sql
-- Automatically created by pgvector embedding store
CREATE INDEX idx_rag_documents_library ON rag_documents ((metadata->>'library'));
```

### Memory Usage

Filtering reduces the working set:

- Quarkus only: ~250 docs → ~3,750 chunks
- Quarkus + Hibernate: ~253 docs → ~4,250 chunks
- Quarkus + 5 libraries: ~280 docs → ~5,000 chunks

All well within memory limits.

## Examples

### Example 1: Hibernate-Specific Query

```java
// User asks Hibernate-specific question
String query = "How do I configure Hibernate second-level cache?";

// Set libraries context
Map<String, String> variables = new HashMap<>();
variables.put("libraries", "hibernate-orm");
ragRequestContext.setVariables(variables);

// Results will only come from Hibernate documentation
assistant.assist(query);
```

**Result:** High-quality Hibernate User Guide sections about second-level cache.

### Example 2: Auto-Detection Based on Dependencies

```java
// At application startup, detect dependencies
Set<String> activeLibraries = detectLibraries();
// activeLibraries = ["quarkus", "hibernate-orm", "smallrye-config"]

// Configure as default
System.setProperty("chappie.rag.libraries",
                   String.join(",", activeLibraries));

// All subsequent queries automatically filtered to relevant docs
```

### Example 3: Extension-Specific Query

```java
// User asks about specific extension
String query = "How do I use Hibernate Panache?";

Map<String, String> variables = new HashMap<>();
variables.put("libraries", "quarkus,hibernate-orm");
variables.put("extension", "quarkus-hibernate-orm-panache");
ragRequestContext.setVariables(variables);

assistant.assist(query);
```

**Result:** Only Panache-related docs from Quarkus and Hibernate.

## Troubleshooting

### Issue: No results returned

**Cause:** Library filter too restrictive or documents missing `library` metadata.

**Solution:**
1. Check configured libraries: `chappie.rag.libraries`
2. Verify documents have `library` metadata
3. Use broader filter (e.g., add `quarkus` to library list)

### Issue: Wrong documentation returned

**Cause:** Multiple libraries contain similar content.

**Solution:**
1. Use more specific library filter
2. Combine with extension filter
3. Add library-specific keywords to query

### Issue: Slow query performance

**Cause:** Large number of libraries or missing index.

**Solution:**
1. Reduce number of active libraries
2. Ensure `library` metadata field is indexed
3. Check PostgreSQL query plan

## Migration Guide

### From Extension-Only Filtering

**Before:**
```java
// Old approach - extension filtering only
variables.put("extension", "quarkus-hibernate-orm");
```

**After:**
```java
// New approach - library + extension filtering
variables.put("libraries", "quarkus,hibernate-orm");
variables.put("extension", "quarkus-hibernate-orm");
```

### Backward Compatibility

Library filtering is **fully backward compatible**:

- If no library filter specified, uses configured default
- Default is `quarkus` (same as before)
- Extension filtering continues to work unchanged
- SearchRequest supports both old and new fields

**Old code continues to work:**
```java
// Still works - uses default library filter
SearchRequest req = new SearchRequest(query, maxResults, extension, null);
```

## Best Practices

### 1. Configure Libraries at Startup

Set `chappie.rag.libraries` based on detected dependencies:

```java
@BuildStep
void configureLibraries(CurateOutcomeBuildItem curateOutcome) {
    Set<String> libs = detectLibraries(curateOutcome);
    config.produce(new ConfigPropertyBuildItem(
        "chappie.rag.libraries",
        String.join(",", libs)
    ));
}
```

### 2. Use Library-Specific Variables for Precision

For library-specific questions, narrow the filter:

```java
if (isHibernateQuestion(query)) {
    variables.put("libraries", "hibernate-orm");
}
```

### 3. Combine with Extension Filtering

Use both for maximum precision:

```java
variables.put("libraries", "quarkus,hibernate-orm");
variables.put("extension", "quarkus-hibernate-orm-panache");
```

### 4. Log Filtering Decisions

Enable debug logging to see filtering in action:

```properties
quarkus.log.category."org.chappiebot.rag".level=DEBUG
```

Output:
```
Restricting search to libraries: [quarkus,hibernate-orm]
Restricting search to extension: quarkus-hibernate-orm-panache
```

## API Reference

### RetrievalProvider.search()

```java
public List<SearchMatch> search(
    String queryMessage,
    int maxResults,
    String restrictToExtension,
    String restrictToLibraries,
    boolean useMetadataBoost
)
```

**Parameters:**
- `queryMessage` - The search query
- `maxResults` - Maximum number of results
- `restrictToExtension` - Extension filter (optional)
- `restrictToLibraries` - Library filter (optional, comma-separated)
- `useMetadataBoost` - Apply metadata boosting

**Returns:** List of SearchMatch objects

### SearchRequest

```java
public record SearchRequest(
    String queryMessage,
    Integer maxResults,
    String extension,
    String libraries
)
```

**Fields:**
- `queryMessage` - Required. The search query.
- `maxResults` - Optional. Max results (default: configured value).
- `extension` - Optional. Extension filter.
- `libraries` - Optional. Library filter (comma-separated).
