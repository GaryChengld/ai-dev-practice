package com.example.aipractice.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Conversation memory selected for a final chat request.
 *
 * @param summary rolling summary available to the model
 * @param recentMessages raw messages not represented by that summary
 * @param compactionSucceeded whether the requested summary compaction succeeded
 */
public record ChatMemoryContext(
        ConversationSummary summary,
        List<Message> recentMessages,
        boolean compactionSucceeded
) {

    public ChatMemoryContext {
        recentMessages = List.copyOf(recentMessages);
    }
}
