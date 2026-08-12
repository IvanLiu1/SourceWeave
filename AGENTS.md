# AGENTS.md

This is the working agreement for agents modifying the SourceWeave repository.

## Project

SourceWeave is an enterprise RAG knowledge-management system:

- Spring Boot backend (Java 17)
- Vue 3 + TypeScript frontend
- MySQL for durable application data
- Redis for cache, sessions, rate limits, and short-lived chat state
- Elasticsearch for retrieval
- Kafka for asynchronous document processing
- MinIO for object storage

Prefer small, end-to-end fixes that follow existing repository patterns.

## First Read

At the start of a new thread, inspect in this order:

1. `AGENTS.md`
2. repo root `.env` and `src/main/resources/application*.yml`
3. `frontend/.env*`
4. current runtime state: backend process, frontend dev server, ports, and browser behavior

Do not assume generic Spring Boot or Vite defaults before checking the local setup.

## Local Runtime

### Backend

The backend is normally started from the IDE with hot deployment.

- Default local address: `http://localhost:8081`
- Do not restart it by default.
- After Java changes, compile to trigger hot reload:

```bash
mvn -q -DskipTests compile
```

Restart only when the user asks or runtime evidence shows hot reload is inactive.

### Frontend

The frontend is normally already running in Vite dev mode.

- Default local address: `http://localhost:9527`
- `pnpm run dev` uses Vite `test` mode.
- Use Node 20 and pnpm 10 when reproducing CI behavior.
- Do not use a full build/deploy loop when the live dev server is sufficient.

### Infrastructure

Typical local dependencies are:

- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- Elasticsearch: `localhost:9200`
- Kafka: `localhost:9092`
- MinIO: `localhost:9000`

Check actual listeners and configuration before diagnosing connectivity.

## Configuration

Backend configuration comes from:

- repo root `.env`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`

Frontend configuration comes from:

- `frontend/.env`
- `frontend/.env.test`
- `frontend/.env.prod`
- Vite proxy/runtime configuration

In local development, browser requests may use `/proxy-default/...` while the target backend API is
`http://localhost:8081/api/v1`.

Do not casually `source .env`; values may not be shell-safe. Never print secrets, tokens, passwords,
API keys, or complete token-bearing URLs. Redact sensitive values in logs and command output.

## Engineering Guardrails

### Persistence and chat history

- MySQL is the durable source of truth for conversation history.
- Redis is only for short-lived context and session state.
- Preserve citation/reference mappings through persistence and history rendering.
- When history is missing, verify the browser request, backend response, MySQL rows, and loaded runtime code.

### Multi-tenancy

SourceWeave authorization depends on user identity, organization tags, role, and sometimes explicit
query parameters. Verify all relevant filters when changing queries, documents, or admin views.

### Retrieval and citations

- Treat rerank scores as ordering and observability signals, not answer confidence.
- Do not suppress an answer or show a confidence warning solely because of an absolute rerank score.
- Final abstention should depend on whether cited evidence entails the answer.
- Preserve `fileMd5`, `chunkId`, `pageNumber`, and `anchorText` through retrieval and preview flows.

### Tests

- Prefer targeted tests before the full suite.
- Unit tests must not depend on local MySQL, Kafka, Redis, Elasticsearch, or MinIO unless explicitly
  written as integration tests.
- Be aware that the root `.env` can affect Spring profile selection; verify the active test profile.
- Do not interpret sandbox-blocked local network access as an application failure without confirming it.

## Validation

Backend:

```bash
mvn -q -DskipTests compile
mvn test
```

Frontend:

```bash
cd frontend
pnpm exec eslint <file>
pnpm typecheck
pnpm build
```

Use only the checks relevant to the change, but validate both sides when behavior crosses the frontend
and backend boundary.

For UI or interaction changes, use a real browser. Common pages:

- `http://localhost:9527/#/chat`
- `http://localhost:9527/#/chat-history`

Inspect the visible behavior, network requests and response bodies, and console output before deciding
whether the issue is frontend, backend, data, or environment.

## Git Workflow

Keep unrelated user changes and untracked files out of commits.

For small, low-risk changes such as documentation, agent instructions, wording, and narrowly scoped
configuration cleanup:

1. stay on `main`
2. stage only the intended files
3. commit with a concise message
4. push directly to `origin/main`
5. do not create a branch or pull request

For business logic, security-sensitive behavior, schema or data migrations, dependency upgrades, or
larger cross-layer changes, use a `codex/` branch and a pull request unless the user explicitly requests
direct delivery.

Never publish `evaluation/runs/` or other local experiment artifacts unless the user explicitly names
the report to publish.

## RAG Evaluation

- Committed suite: `rag-eval-en-v1` (120 answerable HotpotQA cases and 30 unanswerable SQuAD 2.0 cases).
- Current answer contract: `rag-eval-answer-v5`.
- Keep the Elasticsearch index fingerprint frozen when comparing prompt or rerank variants.
- Answer-only reruns do not require passage re-vectorization.
- The current `0.2` hard gate and `0.4` soft warning are known technical debt and must not be treated as
  reliable answer-confidence thresholds.
- The current default reranker is `gte-rerank-v2`. Evaluate `qwen3-rerank` in Q&A instruction mode on
  production-like queries before changing the default.
- Track unnecessary refusal, unsupported-answer rate, retries, and latency in addition to benchmark scores.

## Done Criteria

A task is complete only when the relevant evidence is clear:

1. the intended code or documentation changed
2. appropriate compile, test, lint, type-check, or build checks ran
3. the active runtime loaded the new code when runtime behavior matters
4. browser behavior and network responses were verified for UI work
5. MySQL, Redis, Elasticsearch, Kafka, or MinIO state was checked when the symptom is data-related

If a required runtime is not running, report that limitation explicitly instead of claiming end-to-end
verification.
