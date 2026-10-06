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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeCategoryRouterTests {

    private static final String ROUTER_PROMPT = """
            You route the user's question to one available knowledge category.

            Available categories:
            {{categories}}

            Choose exactly one category from the list above.
            If none applies, return "none".
            """;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final KnowledgeCategoryRouter router = new KnowledgeCategoryRouter(
            ChatClient.create(chatModel),
            new AiPrompts(
                    "Assist the user.",
                    "Analyze the ticket.",
                    ROUTER_PROMPT,
                    "Rewrite knowledge questions.",
                    "Summarize the conversation.",
                    "Conversation summary: {{summary}}"
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

        RoutingDecision decision = router.route(
                "How long do refunds take?",
                Set.of("ticket", "refund", "security")
        );

        assertThat(decision.category()).isEqualTo("refund");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(SystemMessage.class);
                    assertThat(instruction.getText())
                            .contains("Available categories:", "- refund", "- security", "- ticket")
                            .doesNotContain("{{categories}}");
                })
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(UserMessage.class);
                    assertThat(instruction.getText()).contains("How long do refunds take?");
                });
    }

    @Test
    void acceptsNewCategoryWithoutAJavaAllowlistChange() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"category":"account"}
                """));

        assertThat(router.route(
                "Question about an account",
                Set.of("account")
        ).category())
                .isEqualTo("account");
    }

    @Test
    void rejectsCategoryNotPresentInTheRuntimeSet() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {"category":"billing"}
                """));

        assertThatThrownBy(() -> router.route("Question", Set.of("account")))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned an invalid routing decision");
    }

    @Test
    void rejectsMissingCategory() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{}"));

        assertThatThrownBy(() -> router.route("Question", Set.of("refund")))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned an invalid routing decision");
    }

    @Test
    void wrapsModelFailures() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> router.route("Question", Set.of("refund")))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI knowledge category routing failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
