package org.chappiebot.search;

/**
 * Search request for RAG-based document retrieval.
 *
 * @param queryMessage The search query
 * @param maxResults Maximum number of results (optional, defaults to configured value)
 * @param extension Filter by Quarkus extension (optional, e.g., "quarkus-hibernate-orm")
 * @param libraries Filter by libraries (optional, comma-separated, e.g., "quarkus,hibernate-orm")
 */
public record SearchRequest(String queryMessage, Integer maxResults, String extension, String libraries) {
}
