package com.example.aipractice.service;

import java.util.Objects;

/**
 * Rolling summary state for a conversation.
 *
 * @param text compact representation of older conversation messages
 * @param summarizedMessageCount number of raw messages already included in the summary
 */
public record ConversationSummary(String text, int summarizedMessageCount) {

    public ConversationSummary {
        Objects.requireNonNull(text, "text must not be null");
        if (summarizedMessageCount < 0) {
            throw new IllegalArgumentException("summarizedMessageCount must not be negative");
        }
    }

    /**
     * Creates summary state for a new conversation.
     *
     * @return empty summary state
     */
    public static ConversationSummary empty() {
        return new ConversationSummary("", 0);
    }
}
