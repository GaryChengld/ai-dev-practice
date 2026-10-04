package com.example.aipractice.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects immutable conversation context for each AI operation.
 */
@Component
public class ConversationContextManager {

    private static final int QUERY_REWRITE_MAX_MESSAGES = 4;
    private static final int CHAT_MAX_MESSAGES = 10;

    /**
     * Returns the recent context needed to resolve references in a search query.
     *
     * @param history complete conversation history
     * @return an immutable snapshot of at most four recent messages
     */
    public List<Message> forQueryRewrite(List<Message> history) {
        return last(history, QUERY_REWRITE_MAX_MESSAGES);
    }

    /**
     * Returns the recent context needed to answer the latest user message.
     *
     * @param history complete conversation history
     * @return an immutable snapshot of at most ten recent messages
     */
    public List<Message> forChat(List<Message> history) {
        return last(history, CHAT_MAX_MESSAGES);
    }

    private List<Message> last(List<Message> history, int maxMessages) {
        int fromIndex = Math.max(0, history.size() - maxMessages);
        return List.copyOf(history.subList(fromIndex, history.size()));
    }
}
