package com.example.aipractice.client;

import com.example.aipractice.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiAiClientTests {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final GeminiAiClient client = new GeminiAiClient(chatModel);

    @Test
    void returnsTextGeneratedBySpringAi() {
        when(chatModel.call("Hello")).thenReturn("Hello from Gemini");

        assertThat(client.chat("Hello")).isEqualTo("Hello from Gemini");
        verify(chatModel).call("Hello");
    }

    @Test
    void rejectsAnEmptyModelResponse() {
        when(chatModel.call("Hello")).thenReturn("");

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("Gemini returned no text content");
    }

    @Test
    void wrapsSpringAiFailures() {
        when(chatModel.call("Hello")).thenThrow(new IllegalStateException("Provider failed"));

        assertThatThrownBy(() -> client.chat("Hello"))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("Gemini API request failed")
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
