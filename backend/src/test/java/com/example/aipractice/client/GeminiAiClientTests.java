package com.example.aipractice.client;

import com.example.aipractice.config.AiProviderProperties;
import com.example.aipractice.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiAiClientTests {

    private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiProviderProperties properties = new AiProviderProperties(
            "gemini-3-flash-preview",
            SYSTEM_PROMPT
    );
    private final GeminiAiClient client = new GeminiAiClient(chatModel, properties);

    @Test
    void sendsSystemAndUserMessagesAndReturnsGeneratedText() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("Hello from Gemini"));

        assertThat(client.chat("Hello")).isEqualTo("Hello from Gemini");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertThat(promptCaptor.getValue().getInstructions())
                .hasSize(2)
                .satisfiesExactly(
                        instruction -> {
                            assertThat(instruction).isInstanceOf(SystemMessage.class);
                            assertThat(instruction.getText()).isEqualTo(SYSTEM_PROMPT);
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText()).isEqualTo("Hello");
                        }
                );
    }

    @Test
    void rejectsAnEmptyModelResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(""));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("Gemini returned no text content");
    }

    @Test
    void wrapsSpringAiFailures() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("Gemini API request failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
