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
│   └── ChatResponse.java
├── exception/
│   └── AiProviderException.java
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
`application.yml`. Local `.env` files are ignored by Git and imported by the
application through Spring Boot's optional configuration import.

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
  -d '{"message":"Explain dependency injection briefly."}'
```

Structured ticket analysis:

```shell
curl -X POST http://localhost:8080/api/ai/analyze-ticket \
  -H "Content-Type: application/json" \
  -d '{"message":"The production checkout API returns 500 errors."}'
```
