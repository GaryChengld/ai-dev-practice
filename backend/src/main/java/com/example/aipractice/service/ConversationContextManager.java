package com.example.aipractice.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects immutable conversation context for each AI operation.
 */
@Component
public class ConversationContextManager {

    private static final int SUMMARY_THRESHOLD_MESSAGES = 10;
    private static final int RECENT_CHAT_MAX_MESSAGES = 4;

    /**
     * Returns every raw message not represented by the existing rolling summary.
     *
     * @param history complete conversation history
     * @param summarizedMessageCount number of leading messages already summarized
     * @return an immutable snapshot of the unsummarized tail
     */
    public List<Message> forQueryRewrite(
            List<Message> history,
            int summarizedMessageCount
    ) {
        validateSummaryPosition(history, summarizedMessageCount);
        return List.copyOf(history.subList(summarizedMessageCount, history.size()));
    }

    /**
     * Returns the recent context needed to answer the latest user message.
     *
     * @param history complete conversation history
     * @return an immutable snapshot of all messages up to the summary threshold,
     *         or the four most recent messages after that threshold
     */
    public List<Message> forChat(List<Message> history) {
        int maxMessages = history.size() > SUMMARY_THRESHOLD_MESSAGES
                ? RECENT_CHAT_MAX_MESSAGES
                : SUMMARY_THRESHOLD_MESSAGES;
        return last(history, maxMessages);
    }

    /**
     * Returns messages that are old enough to add to the rolling summary and
     * have not already been compacted.
     *
     * @param history complete raw conversation history
     * @param summarizedMessageCount number of leading messages already summarized
     * @return an immutable snapshot of newly old messages, or an empty list
     */
    public List<Message> forSummaryCompaction(
            List<Message> history,
            int summarizedMessageCount
    ) {
        validateSummaryPosition(history, summarizedMessageCount);
        if (history.size() <= SUMMARY_THRESHOLD_MESSAGES) {
            return List.of();
        }

        int compactUntil = history.size() - RECENT_CHAT_MAX_MESSAGES;
        if (summarizedMessageCount >= compactUntil) {
            return List.of();
        }
        return List.copyOf(history.subList(summarizedMessageCount, compactUntil));
    }

    private void validateSummaryPosition(
            List<Message> history,
            int summarizedMessageCount
    ) {
        if (summarizedMessageCount < 0 || summarizedMessageCount > history.size()) {
            throw new IllegalArgumentException(
                    "summarizedMessageCount must identify a position in history"
            );
        }
    }

    private List<Message> last(List<Message> history, int maxMessages) {
        int fromIndex = Math.max(0, history.size() - maxMessages);
        return List.copyOf(history.subList(fromIndex, history.size()));
    }
}
