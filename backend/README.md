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

The optional `GEMINI_MODEL`, `AI_PROMPTS_CHAT_ASSISTANT`, and
`AI_PROMPTS_TICKET_ANALYZER` environment variables override the defaults in
`application.yml`. Prompt settings are Markdown filenames resolved from
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

The assistant can also retrieve the support ticket SLA policy from
`src/main/resources/knowledge/ticket-sla.md`. This keeps application knowledge
outside the model and lets it answer questions such as "What is our SLA for a
HIGH-priority ticket?" The chat flow can combine this knowledge tool with the
ticket tools to explain both a ticket's current state and its required response
time.

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
