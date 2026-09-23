package com.example.aipractice.service;

import com.example.aipractice.config.AiPromptProperties;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.exception.AiProviderException;
import com.example.aipractice.exception.ConversationNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTests {

    private static final String CHAT_PROMPT = "You are a helpful assistant.";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiPromptProperties prompts = new AiPromptProperties(
            CHAT_PROMPT,
            "Analyze the ticket."
    );
    private final AiChatService service = new AiChatService(
            ChatClient.create(chatModel),
            prompts
    );

    @Test
    void sendsChatPromptAndReturnsGeneratedText() {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse("Hello from AI"));

        ChatResponse response = service.chat(null, "Hello");

        assertThat(response.message()).isEqualTo("Hello from AI");
        assertThatCode(() -> UUID.fromString(response.conversationId()))
                .doesNotThrowAnyException();
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .hasSize(2)
                .satisfiesExactly(
                        instruction -> {
                            assertThat(instruction).isInstanceOf(SystemMessage.class);
                            assertThat(instruction.getText()).isEqualTo(CHAT_PROMPT);
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText()).isEqualTo("Hello");
                        }
                );
    }

    @Test
    void continuesAnExistingConversationWithPreviousMessages() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(modelResponse("First answer"))
                .thenReturn(modelResponse("Second answer"));

        ChatResponse firstResponse = service.chat("", "First question");
        ChatResponse secondResponse = service.chat(
                firstResponse.conversationId(),
                "Second question"
        );

        assertThat(secondResponse.conversationId()).isEqualTo(firstResponse.conversationId());
        assertThat(secondResponse.message()).isEqualTo("Second answer");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, org.mockito.Mockito.times(2)).call(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).getInstructions())
                .hasSize(4)
                .satisfiesExactly(
                        instruction -> {
                            assertThat(instruction).isInstanceOf(SystemMessage.class);
                            assertThat(instruction.getText()).isEqualTo(CHAT_PROMPT);
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText()).isEqualTo("First question");
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(AssistantMessage.class);
                            assertThat(instruction.getText()).isEqualTo("First answer");
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText()).isEqualTo("Second question");
                        }
                );
    }

    @Test
    void rejectsAnUnknownConversationId() {
        assertThatThrownBy(() -> service.chat("unknown-id", "Hello"))
                .isInstanceOf(ConversationNotFoundException.class)
                .hasMessage("Conversation not found: unknown-id");
    }

    @Test
    void rejectsAnEmptyModelResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse(""));

        assertThatThrownBy(() -> service.chat(null, "Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider returned no chat content");
    }

    @Test
    void wrapsSpringAiFailures() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> service.chat(null, "Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI chat request failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private org.springframework.ai.chat.model.ChatResponse modelResponse(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage(text)))
        );
    }
}
