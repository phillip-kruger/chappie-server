package org.chappiebot.store;

/**
 * Exception thrown when chat memory store operations fail.
 * Wraps underlying database exceptions with context-specific messages.
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public class ChatMemoryStoreException extends RuntimeException {

    public ChatMemoryStoreException(String message) {
        super(message);
    }

    public ChatMemoryStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for failed memory operations.
     */
    public static ChatMemoryStoreException forMemoryOperation(String operation, Object memoryId, Throwable cause) {
        return new ChatMemoryStoreException(
            String.format("Failed to %s for memory ID '%s'", operation, memoryId),
            cause
        );
    }

    /**
     * Creates an exception for failed list operations.
     */
    public static ChatMemoryStoreException forListOperation(String operation, Throwable cause) {
        return new ChatMemoryStoreException(
            String.format("Failed to %s", operation),
            cause
        );
    }
}
