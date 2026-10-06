# AI Practice Backend

A Spring Boot REST project for practising Spring AI integrations.

## Structure

```text
src/main/java/com/example/aipractice/
├── AiPracticeApplication.java
├── config/
│   ├── AiConfiguration.java
│   └── AiPromptProperties.java
├── controller/
│   ├── AiChatController.java
│   └── AiExceptionHandler.java
├── domain/
│   ├── Category.java
│   ├── Priority.java
│   └── TicketAnalysis.java
├── dto/
│   ├── ApiError.java
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   └── TicketAnalysisRequest.java
├── exception/
│   ├── AiProviderException.java
│   └── ConversationNotFoundException.java
└── service/
    ├── AiChatService.java
    └── TicketAnalysisService.java
```

Both services reuse one shared Spring AI `ChatClient` bean. `AiChatService`
returns free-form text, while `TicketAnalysisService` uses structured output to
map the model response to `TicketAnalysis`.

## Gemini credentials

The project reads the Gemini API key from the `GEMINI_API_KEY` environment
variable. The key is not stored in `application.yml` and must not be committed.

For the current PowerShell session:

```powershell
$env:GEMINI_API_KEY = "your-key"
mvn spring-boot:run
```

The optional `GEMINI_MODEL` and `AI_PROMPTS_*` environment variables override
the defaults in `application.yml`. Prompt settings are Markdown filenames resolved from
`src/main/resources/prompts`. Local `.env` files are ignored by Git and
imported by the application through Spring Boot's optional configuration
import.

## Run

Requires Java 17 or newer and Maven 3.6.3 or newer.

```shell
mvn spring-boot:run
```

## API

General chat:

```shell
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":null,"message":"Explain dependency injection briefly."}'
```

The response includes a generated `conversationId`. Send that identifier in a
later request to continue the conversation:

```shell
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"generated-id","message":"Show me an example."}'
```

Conversation history is stored in memory and is cleared when the application
restarts. Supplying an identifier that is not in memory returns HTTP 404.

For ticket-attention questions, the assistant checks the ticket status first.
It checks priority only for `OPEN` or `IN_PROGRESS` tickets; it does not check
priority for `RESOLVED`, missing, or unrecognized statuses. `CRITICAL` and
`HIGH` priorities need attention, `MEDIUM` should be monitored but is not
urgent, and `LOW` generally does not need immediate attention.

The assistant can search the Markdown knowledge base in
`src/main/resources/knowledge`. It currently contains ticket SLA, refund, and
password policies. On the first knowledge query, the files are loaded, split
into chunks, embedded, and added to an in-memory Spring AI vector store. Each
chat request searches for the three most similar chunks before calling the
model and adds them directly to the prompt as context. The model does not need
to choose or call a knowledge tool. It can still use ticket tools for live data,
allowing a response to combine a ticket's current state with its required
response time. For follow-up messages, a separate AI call first rewrites the
question into a standalone search query using the four most recent messages.
The application continues to store the complete conversation, but it no longer
sends an ever-growing history to the final model. Up to ten stored messages are
sent verbatim. Once history exceeds that threshold, an AI-generated rolling
summary represents the older messages and the four most recent messages remain
verbatim. Only messages that have newly moved out of recent context are added to
the existing summary. The original user message is still used for the final
answer. Any Markdown file added directly under the `knowledge` directory is
discovered automatically; no Java filename list needs to be updated.

```shell
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":null,"message":"Does INC-1002 need attention?"}'
```

Structured ticket analysis:

```shell
curl -X POST http://localhost:8080/api/ai/analyze-ticket \
  -H "Content-Type: application/json" \
  -d '{"message":"The production checkout API returns 500 errors."}'
```
