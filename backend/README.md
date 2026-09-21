# AI Practice Backend

A minimal Spring Boot REST project prepared for a future AI provider integration.

## Structure

```text
src/main/java/com/example/aipractice/
├── AiPracticeApplication.java
├── client/
│   ├── AiClient.java
│   └── GeminiAiClient.java
├── config/
│   └── AiProviderProperties.java
├── controller/
│   └── AiChatController.java
├── dto/
│   ├── ChatRequest.java
│   └── ChatResponse.java
└── service/
    └── AiChatService.java
```

`GeminiAiClient` uses Spring AI's provider-neutral `ChatModel`. The Google GenAI
starter supplies the model implementation and handles the Gemini API request and
response mapping.

## Gemini credentials

The project reads the Gemini API key from the `GEMINI_API_KEY` environment
variable. The key is not stored in `application.yml` and must not be committed.

For the current PowerShell session:

```powershell
$env:GEMINI_API_KEY = "your-key"
mvn spring-boot:run
```

The optional `GEMINI_MODEL` and `AI_PROVIDER_SYSTEM_PROMPT` environment
variables override the defaults in `application.yml`.
Local `.env` files are ignored by Git and imported by this application through
Spring Boot's optional configuration import.

## Run

Requires Java 17 or newer and Maven 3.6.3 or newer.

```shell
mvn spring-boot:run
```

The REST endpoint is `POST /api/ai/chat` with `Content-Type: application/json`.

Example request:

```shell
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Explain dependency injection briefly."}'
```
