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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryServiceTests {

    private static final String SUMMARY_PROMPT = """
            Update the rolling summary.

            <existing-summary>
            {{existingSummary}}
            </existing-summary>

            <new-messages>
            {{messagesToCompact}}
            </new-messages>
            """;

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ConversationSummaryService summaryService =
            new ConversationSummaryService(
                    ChatClient.create(chatModel),
                    new AiPrompts(
                            "Assist the user.",
                            "Analyze the ticket.",
                            "Route knowledge questions.",
                            "Rewrite knowledge questions.",
                            SUMMARY_PROMPT,
                            "Conversation summary: {{summary}}"
                    )
            );

    ConversationSummaryServiceTests() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void updatesExistingSummaryWithNewlyOldMessages() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "  User asked about INC-1001; it remains open.  "
        ));

        String result = summaryService.summarize(
                "User asked about INC-1001.",
                List.of(
                        new UserMessage("Is it still open?"),
                        new AssistantMessage("Yes, INC-1001 is still open.")
                )
        );

        assertThat(result).isEqualTo("User asked about INC-1001; it remains open.");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .singleElement()
                .satisfies(instruction -> {
                    assertThat(instruction).isInstanceOf(SystemMessage.class);
                    assertThat(instruction.getText())
                            .contains(
                                    "<existing-summary>",
                                    "User asked about INC-1001.",
                                    "<new-messages>",
                                    "USER:\nIs it still open?",
                                    "ASSISTANT:\nYes, INC-1001 is still open."
                            )
                            .doesNotContain("{{existingSummary}}", "{{messagesToCompact}}");
                });
    }

    @Test
    void skipsTheModelWhenThereAreNoMessagesToCompact() {
        assertThat(summaryService.summarize("Existing summary", List.of()))
                .isEqualTo("Existing summary");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void rejectsAnEmptySummary() {
        when(chatModel.call(any(Prompt.class))).thenReturn(response("  "));

        assertThatThrownBy(() -> summaryService.summarize(
                "",
                List.of(new UserMessage("Remember this"))
        ))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned no conversation summary");
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
