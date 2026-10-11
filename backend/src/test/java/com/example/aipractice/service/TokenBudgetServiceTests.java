package com.example.aipractice.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBudgetServiceTests {

    private final TokenBudgetService tokenBudgetService = new TokenBudgetService();

    @Test
    void estimatesTextUsingThreeCharactersPerTokenRoundedUp() {
        assertThat(tokenBudgetService.estimateTokens((String) null)).isZero();
        assertThat(tokenBudgetService.estimateTokens("")).isZero();
        assertThat(tokenBudgetService.estimateTokens("abc")).isEqualTo(1);
        assertThat(tokenBudgetService.estimateTokens("abcd")).isEqualTo(2);
    }

    @Test
    void includesRoleOverheadForEveryMessage() {
        int estimate = tokenBudgetService.estimateTokens(List.of(
                new UserMessage("abc"),
                new AssistantMessage("abcdef")
        ));

        assertThat(estimate).isEqualTo(19);
    }

    @Test
    void estimatesACompletePromptWithSystemAndUserMessageOverhead() {
        int estimate = tokenBudgetService.estimatePromptTokens(
                "abc",
                List.of(new AssistantMessage("abcdef")),
                "abcdefghi"
        );

        assertThat(estimate).isEqualTo(30);
    }

    @Test
    void includesToolNamesDescriptionsAndInputSchemasInPromptEstimate() {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("lookup");
        when(definition.description()).thenReturn("Find item");
        when(definition.inputSchema()).thenReturn("{}");
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(definition);

        int estimate = tokenBudgetService.estimatePromptTokens(
                "abc",
                List.of(new AssistantMessage("abcdef")),
                "abcdefghi",
                List.of(callback)
        );

        assertThat(estimate).isEqualTo(44);
    }

    @Test
    void treatsTheBudgetAsInclusive() {
        assertThat(tokenBudgetService.fitsBudget(2_000, 2_000)).isTrue();
        assertThat(tokenBudgetService.fitsBudget(2_001, 2_000)).isFalse();
    }
}
