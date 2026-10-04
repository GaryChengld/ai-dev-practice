package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationContextManagerTests {

    private final ConversationContextManager contextManager =
            new ConversationContextManager();

    @Test
    void selectsLastFourMessagesForQueryRewrite() {
        List<Message> history = messages(7);

        List<Message> context = contextManager.forQueryRewrite(history);

        assertThat(context)
                .extracting(Message::getText)
                .containsExactly("Message 4", "Message 5", "Message 6", "Message 7");
        assertThat(history)
                .extracting(Message::getText)
                .containsExactly(
                        "Message 1",
                        "Message 2",
                        "Message 3",
                        "Message 4",
                        "Message 5",
                        "Message 6",
                        "Message 7"
                );
    }

    @Test
    void selectsLastTenMessagesForChat() {
        List<Message> context = contextManager.forChat(messages(12));

        assertThat(context)
                .extracting(Message::getText)
                .containsExactly(
                        "Message 3",
                        "Message 4",
                        "Message 5",
                        "Message 6",
                        "Message 7",
                        "Message 8",
                        "Message 9",
                        "Message 10",
                        "Message 11",
                        "Message 12"
                );
    }

    @Test
    void returnsAnImmutableCopyWhenHistoryIsWithinTheLimit() {
        List<Message> history = messages(2);

        List<Message> context = contextManager.forChat(history);

        assertThat(context).isEqualTo(history).isNotSameAs(history);
        assertThatThrownBy(() -> context.add(new UserMessage("New message")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private List<Message> messages(int count) {
        return new ArrayList<>(IntStream.rangeClosed(1, count)
                .mapToObj(number -> (Message) new UserMessage("Message " + number))
                .toList());
    }
}
