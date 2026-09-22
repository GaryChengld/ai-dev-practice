package com.example.aipractice.service;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.domain.Category;
import com.example.aipractice.domain.Priority;
import com.example.aipractice.domain.TicketAnalysis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketAnalysisServiceTests {

    private static final String TICKET_PROMPT = "Analyze software support tickets.";
    private static final String TICKET_MESSAGE =
            "Our production checkout API returns 500 errors.";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiPromptProperties prompts = new AiPromptProperties(
            "Assist with Java questions.",
            TICKET_PROMPT
    );
    private final TicketAnalysisService service = new TicketAnalysisService(
            ChatClient.create(chatModel),
            prompts
    );

    @Test
    void convertsStructuredModelOutputToTicketAnalysis() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("""
                {
                  "category": "TECHNICAL",
                  "priority": "CRITICAL",
                  "summary": "Production checkout API failing after deployment",
                  "suggestedAction": "Rollback the latest deployment and investigate the 500 errors"
                }
                """));

        TicketAnalysis analysis = service.analyze(TICKET_MESSAGE);

        assertThat(analysis.category()).isEqualTo(Category.TECHNICAL);
        assertThat(analysis.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(analysis.summary())
                .isEqualTo("Production checkout API failing after deployment");
        assertThat(analysis.suggestedAction())
                .isEqualTo("Rollback the latest deployment and investigate the 500 errors");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(SystemMessage.class);
                    assertThat(instruction.getText()).isEqualTo(TICKET_PROMPT);
                })
                .anySatisfy(instruction -> {
                    assertThat(instruction).isInstanceOf(UserMessage.class);
                    assertThat(instruction.getText()).contains(TICKET_MESSAGE);
                });
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
