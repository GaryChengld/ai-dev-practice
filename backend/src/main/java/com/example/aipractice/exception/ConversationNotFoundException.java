package com.example.aipractice.exception;

/**
 * Indicates that a requested chat conversation does not exist in memory.
 */
public class ConversationNotFoundException extends RuntimeException {

    /**
     * Creates an exception for an unknown conversation identifier.
     *
     * @param conversationId unknown conversation identifier
     */
    public ConversationNotFoundException(String conversationId) {
        super("Conversation not found: " + conversationId);
    }
}
