package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeQueryRewriterTests {

    private static final String REWRITER_PROMPT = "Rewrite into a standalone query.";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final KnowledgeQueryRewriter rewriter = new KnowledgeQueryRewriter(
            ChatClient.create(chatModel),
            new AiPrompts(
                    "Assist the user.",
                    "Analyze the ticket.",
                    "Route knowledge questions.",
                    REWRITER_PROMPT,
                    "Summarize the conversation.",
                    "Conversation summary: {{summary}}"
            )
    );

    KnowledgeQueryRewriterTests() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void rewritesLatestQuestionUsingConversationHistory() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "  How long do approved refunds take?  "
        ));
        List<org.springframework.ai.chat.messages.Message> history = List.of(
                new UserMessage("Tell me about approved refunds."),
                new AssistantMessage("Approved refunds take 3-5 business days.")
        );

        String result = rewriter.rewrite("How long do they take?", history);

        assertThat(result).isEqualTo("How long do approved refunds take?");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .hasSize(4)
                .satisfiesExactly(
                        instruction -> {
                            assertThat(instruction).isInstanceOf(SystemMessage.class);
                            assertThat(instruction.getText()).isEqualTo(REWRITER_PROMPT);
                        },
                        instruction -> assertThat(instruction.getText())
                                .isEqualTo("Tell me about approved refunds."),
                        instruction -> assertThat(instruction.getText())
                                .isEqualTo("Approved refunds take 3-5 business days."),
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText()).isEqualTo("How long do they take?");
                        }
                );
    }

    @Test
    void rejectsAnEmptyRewrittenQuery() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("  "));

        assertThatThrownBy(() -> rewriter.rewrite("What about it?", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned no rewritten query");
    }

    @Test
    void wrapsModelFailures() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> rewriter.rewrite("What about it?", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI knowledge query rewrite failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
