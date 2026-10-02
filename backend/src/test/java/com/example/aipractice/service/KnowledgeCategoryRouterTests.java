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

class KnowledgeCategoryRouterTests {

    private static final String ROUTER_PROMPT = """
            Select exactly one category: ticket, refund, security, or none.
            """;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final KnowledgeCategoryRouter router = new KnowledgeCategoryRouter(
            ChatClient.create(chatModel),
            new AiPrompts(
                    "Assist the user.",
                    "Analyze the ticket.",
                    ROUTER_PROMPT
            )
    );

    KnowledgeCategoryRouterTests() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void returnsStructuredRoutingDecision() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"category":"refund"}
                """));

        RoutingDecision decision = router.route("How long do refunds take?");

        assertThat(decision.category()).isEqualTo("refund");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(SystemMessage.class);
                    assertThat(instruction.getText()).contains(ROUTER_PROMPT.strip());
                })
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(UserMessage.class);
                    assertThat(instruction.getText()).contains("How long do refunds take?");
                });
    }

    @Test
    void rejectsCategoryOutsideTheAllowedSet() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"category":"billing"}
                """));

        assertThatThrownBy(() -> router.route("Question about an invoice"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned an invalid routing decision");
    }

    @Test
    void wrapsModelFailures() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> router.route("Question"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI knowledge category routing failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
