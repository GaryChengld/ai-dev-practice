package com.example.aipractice.service;

import com.example.aipractice.config.AiPrompts;
import com.example.aipractice.dto.ChatResponse;
import com.example.aipractice.exception.AiProviderException;
import com.example.aipractice.exception.ConversationNotFoundException;
import com.example.aipractice.tools.TicketPriorityTools;
import com.example.aipractice.tools.TicketTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTests {

    private static final String CHAT_PROMPT = "You are a helpful assistant.";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AiPrompts prompts = new AiPrompts(
            CHAT_PROMPT,
            "Analyze the ticket.",
            "Route knowledge questions.",
            "Rewrite knowledge questions."
    );
    private final TicketService ticketService = new TicketService();
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);
    private final KnowledgeQueryRewriter queryRewriter = mock(KnowledgeQueryRewriter.class);
    private final AiChatService service = new AiChatService(
            ChatClient.create(chatModel),
            prompts,
            new TicketTools(ticketService),
            new TicketPriorityTools(ticketService),
            knowledgeService,
            queryRewriter
    );

    AiChatServiceTests() {
        when(chatModel.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(knowledgeService.search(anyString())).thenReturn(List.of());
    }

    @Test
    void retrievesKnowledgeAndAddsItToThePromptBeforeCallingTheModel() {
        when(knowledgeService.search("How long do refunds take?"))
                .thenReturn(List.of(
                        new Document(
                                "# Refund Policy\n\nApproved refunds take 3-5 business days.",
                                Map.of(KnowledgeMetadata.SOURCE, "refund-policy.md")
                        ),
                        new Document(
                                "Customers may request a refund within 30 days.",
                                Map.of(KnowledgeMetadata.SOURCE, "refund-policy.md")
                        ),
                        new Document(
                                "Unrelated metadata must not appear as a source.",
                                Map.of("category", "billing")
                        )
                ));
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse("Hello from AI"));

        ChatResponse response = service.chat(null, "How long do refunds take?");

        assertThat(response.message()).isEqualTo("Hello from AI");
        assertThat(response.sources()).containsExactly("refund-policy.md");
        assertThatCode(() -> UUID.fromString(response.conversationId()))
                .doesNotThrowAnyException();
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt sentPrompt = promptCaptor.getValue();
        assertThat(sentPrompt.getInstructions())
                .hasSize(2)
                .satisfiesExactly(
                        instruction -> {
                            assertThat(instruction).isInstanceOf(SystemMessage.class);
                            assertThat(instruction.getText()).isEqualTo(CHAT_PROMPT);
                        },
                        instruction -> {
                            assertThat(instruction).isInstanceOf(UserMessage.class);
                            assertThat(instruction.getText())
                                    .contains(
                                            "<knowledge-context>",
                                            "# Refund Policy",
                                            "Approved refunds take 3-5 business days.",
                                            "Customers may request a refund within 30 days.",
                                            "User question:\nHow long do refunds take?"
                                    );
                        }
                );
        verify(knowledgeService).search("How long do refunds take?");
        verify(queryRewriter, never()).rewrite(anyString(), any());
        assertThat(sentPrompt.getOptions()).isInstanceOf(ToolCallingChatOptions.class);
        ToolCallingChatOptions options = (ToolCallingChatOptions) sentPrompt.getOptions();
        assertThat(options.getToolCallbacks())
                .extracting(tool -> tool.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "getTicketStatus",
                        "getTicketPriority"
                );
    }

    @Test
    void delegatesKnowledgeRetrievalToKnowledgeService() {
        String message = "Question requiring categorized knowledge";
        when(chatModel.call(any(Prompt.class))).thenReturn(modelResponse("Answer"));

        service.chat(null, message);

        verify(knowledgeService).search(message);
    }

    @Test
    void continuesAnExistingConversationWithPreviousMessages() {
        when(queryRewriter.rewrite(anyString(), any()))
                .thenReturn("Standalone second question");
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
        assertThat(secondResponse.sources()).isEmpty();
        verify(knowledgeService).search("Standalone second question");

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
    void rewritesFollowUpBeforeKnowledgeRetrieval() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(modelResponse("Refunds take 3-5 days."))
                .thenReturn(modelResponse("It can take up to 5 business days."));
        when(queryRewriter.rewrite(anyString(), any()))
                .thenReturn("How long do approved refunds take?");

        ChatResponse firstResponse = service.chat(null, "Tell me about approved refunds.");
        service.chat(firstResponse.conversationId(), "How long do they take?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(queryRewriter).rewrite(
                eq("How long do they take?"),
                historyCaptor.capture()
        );
        assertThat(historyCaptor.getValue())
                .extracting(Message::getText)
                .containsExactly(
                        "Tell me about approved refunds.",
                        "Refunds take 3-5 days."
                );
        verify(knowledgeService).search("How long do approved refunds take?");
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
