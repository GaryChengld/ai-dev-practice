# AI Development Practice

A learning project for building a support assistant with Spring AI, Gemini,
retrieval-augmented generation (RAG), conversational memory, tool calling, and
token-aware context management. The repository also includes a small React UI
for structured support-ticket analysis.

## Features

- Free-form conversational chat with in-memory conversation history
- Structured ticket classification by category and priority
- Tool calling for ticket status and priority lookups
- Markdown knowledge-base ingestion and in-memory vector search
- Category-aware retrieval and follow-up query rewriting
- Rolling conversation summaries with failure-safe memory fallback
- Separate token budgets for query rewriting and final chat prompts
- Tool-schema token estimation and budget-aware RAG document selection
- Real vector similarity scores with deterministic rank-based fallback
- Offline JUnit evaluation of retrieval-order, rank-density,
  similarity-density, and experimental hybrid scoring strategies

## Technology

- Java 17
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Google Gemini chat and embedding models
- Maven
- React 19, TypeScript, and Vite

## Project Structure

```text
ai-dev-practice/
|-- backend/                 Spring Boot REST API and AI workflows
|   |-- src/main/java/       Application code
|   |-- src/main/resources/
|   |   |-- knowledge/       Markdown documents indexed for RAG
|   |   `-- prompts/         Externalized AI prompts
|   `-- src/test/java/       Unit, integration, and offline evaluation tests
|-- frontend/                React ticket-analysis UI
`-- pom.xml                  Maven parent project
```

## How the Chat Pipeline Works

```text
User question
     |
     v
Query rewriting (when history exists and its budget permits)
     |
     v
Knowledge category routing and vector search
     |
     v
Conversation summary and recent-message preparation
     |
     v
Required-context token estimate (system + memory + question + tools)
     |
     v
Allocate remaining budget to RAG documents
     |
     v
Value-density selection using real similarity scores
     |
     v
Final prompt budget validation
     |
     v
Gemini response + sources actually supplied to the model
```

Production document selection uses `similarity / estimated tokens`. If every
retrieved document has a finite score between `0` and `1`, the vector-store
scores are used. If any score is missing or invalid, the complete result set
falls back to `1 / retrieval rank` so incompatible scoring scales are never
mixed.

Hybrid scoring is currently an offline experiment only. It does not alter the
production chat flow.

## Prerequisites

- Java 17 or newer
- Maven 3.6.3 or newer
- A current Node.js LTS release and npm
- A Google Gemini API key

## Configuration

Set the API key before starting the backend.

PowerShell:

```powershell
$env:GEMINI_API_KEY = "your-api-key"
```

Bash or zsh:

```bash
export GEMINI_API_KEY="your-api-key"
```

The backend also supports these environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `GEMINI_API_KEY` | none | Gemini chat and embedding authentication |
| `GEMINI_MODEL` | `gemini-3-flash-preview` | Chat model name |
| `SERVER_PORT` | `8080` | Backend HTTP port |

Application-level context limits are configured in
`backend/src/main/resources/application.yml`:

```yaml
ai:
  context:
    query-rewrite-max-input-tokens: 2000
    final-chat-max-input-tokens: 8000
```

These limits use a deliberately simple three-characters-per-token estimate.
They are guardrails for this learning project, not exact provider token counts.

## Run the Application

Start the backend from the repository root:

```powershell
mvn -pl backend spring-boot:run
```

In a second terminal, start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:6001`. During development, Vite proxies `/api` requests
to the backend at `http://localhost:8080`.

To target another backend, set `VITE_API_BASE_URL` before building or starting
the frontend.

## REST API

### Analyze a Support Ticket

`POST /api/ai/analyze-ticket`

```bash
curl -X POST http://localhost:8080/api/ai/analyze-ticket \
  -H "Content-Type: application/json" \
  -d '{"message":"The production checkout API returns 500 errors."}'
```

Example response:

```json
{
  "category": "TECHNICAL",
  "priority": "HIGH",
  "summary": "Checkout requests are failing in production.",
  "suggestedAction": "Escalate to the on-call engineering team."
}
```

### Start a Conversation

`POST /api/ai/chat`

```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":null,"message":"What is the password policy?"}'
```

The response contains a generated conversation ID, the model response, and
only the knowledge sources supplied to the final prompt:

```json
{
  "conversationId": "generated-id",
  "message": "...",
  "sources": ["password-policy.md"]
}
```

Pass the returned ID to continue the conversation:

```bash
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"generated-id","message":"How often should it change?"}'
```

Conversation state is held in memory and is lost when the backend restarts.
An unknown conversation ID returns HTTP 404. A final prompt whose essential
context exceeds the configured budget returns HTTP 422 with code
`CONTEXT_BUDGET_EXCEEDED` without appending a partial conversation turn.

## Knowledge Base

The backend discovers Markdown files under
`backend/src/main/resources/knowledge/`, parses their YAML front matter,
splits them into chunks, and indexes them in an in-memory vector store on the
first knowledge search.

Current topics include:

- Password policy
- Refund policy
- Ticket service-level agreements

Additional Markdown files placed directly in that directory are discovered
automatically. Each file must contain valid YAML front matter; an optional
`category` value enables category-filtered retrieval.

## Tests and Offline RAG Evaluation

Run the complete backend suite from the repository root:

```powershell
mvn test
```

Run only the RAG selection evaluation harness:

```powershell
mvn -pl backend -Dtest=RagDocumentSelectionEvaluationTests test
```

The offline harness compares document selection against labeled synthetic
cases using relevant-document recall, token utilization, and selected-document
count. It evaluates retrieval order and several relevance-scoring policies
without calling Gemini. These metrics measure document-selection quality, not
final answer correctness; higher token utilization alone does not imply a
better result.

Build the frontend:

```powershell
cd frontend
npm run build
```

## Current Limitations

- Conversations, summaries, tickets, and vector data are stored in memory.
- Token estimates are heuristic and provider formatting is not fully modeled.
- RAG selection is greedy and does not account for document redundancy.
- The frontend currently exposes ticket analysis, while conversational chat is
  available through the REST API.
- The evaluation dataset is synthetic and too small for choosing a production
  scoring policy.
