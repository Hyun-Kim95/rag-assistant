# 아키텍처 (ARCHITECTURE)

## 1. 전체 흐름

```text
[Client/UI]
    ↓
[Spring Boot API]
    ├─ Document Upload / Parse
    ├─ Chunker
    ├─ EmbeddingService (Ollama)
    ├─ VectorStore (PostgreSQL pgvector)
    ├─ Retriever
    ├─ PromptBuilder
    └─ OllamaService (chat)
         ↓
[Answer + Sources]
```

## 2. RAG 파이프라인

### 인덱싱 흐름
```text
문서 업로드 (또는 기동 시 FaqBootstrap)
  → 텍스트 추출 (TXT/MD/PDF)
  → Chunker (일반 문서) / FaqCatalog raw chunk (시스템 FAQ)
  → EmbeddingService (nomic task prefix: search_document:)
  → PostgreSQL(pgvector) 저장
```

### 질의 응답 흐름

**vector-only** (`rag.hybrid-enabled: false`):

```text
사용자 질문
  → EmbeddingService (nomic task prefix: search_query:)
  → pgvector cosine top-k → min-score
  → PromptBuilder (context + question)
  → OllamaService (RagService 경유)
  → Answer + Sources
```

**hybrid** (`rag.hybrid-enabled: true`, 기본):

```text
사용자 질문
  → EmbeddingService (search_query:) + pg_trgm lexical (병렬 leg)
  → leg별 min-score → RrfFusion (RRF, k=60) → top-k
  → PromptBuilder → OllamaService → Answer + Sources
```

**rerank (2단계 retrieval)** (`rag.rerank-enabled: true`, 기본):

```text
사용자 질문
  → 후보 검색 (vector/hybrid, candidate-top-k=30 폭)
  → Reranker → TEI /rerank (cross-encoder bge-reranker-v2-m3)
  → 재정렬 후 상위 rerank-top-n(=8)만 context
  → PromptBuilder → OllamaService → Answer + Sources
  (TEI 실패 시 fallback: 원본 순서 + top-n 컷)
```

설계·이유·fallback·eval은 [`DECISIONS.md`](DECISIONS.md) §14.

## 3. 구현 범위 (요약)

로컬 실행 기준 동작 범위이다. API·예외·DB는 아래 절, 기술 선택은 [`DECISIONS.md`](DECISIONS.md)를 본다.

- 문서 업로드·파싱(TXT/MD/PDF) → chunk · embedding · pgvector 검색 → `POST /api/chat` / `POST /api/chat/stream`
- tool calling 에이전트 — LLM이 도구(`search_documents`·`list_documents`·`read_document`·`summarize_document`)를 호출해 멀티스텝 응답 → `POST /api/agent`. 멀티턴 메모리(무상태 `messages[]`) + 스트리밍 스텝 UI `POST /api/agent/stream`(SSE) (§17·§18 in [`DECISIONS.md`](DECISIONS.md))
- 검색 miss 시 no-answer(LLM 미호출), 답변 시 출처 snippet + `grounded`
- hybrid — `pg_trgm` + RRF, `rag.hybrid-enabled` 기본 `true`
- rerank — 외부 TEI cross-encoder(`bge-reranker-v2-m3`)로 2단계 retrieval, `rag.rerank-enabled` 기본 `true` (§14 in [`DECISIONS.md`](DECISIONS.md))
- 브라우저 UI (`static/`), Swagger, `local` 프로필 debug API
- RAG 평가 자동화: `eval/questions.json` 고정 세트 → `RagEvalRunner`(CLI 플래그) → `eval/reports/` JSON·MD (§11)
- RAG 품질 기록: `RAG_EVAL_v1.md` · `RAG_EVAL_v1.1.md` · `RAG_EVAL_v2.md`
- 운영 관측성: 요청별 구조적 로그(requestId·단계 지연·grounded·rerankFallback) + Health 심화(ollama·db·reranker) (§12)
- 아직 없음: repo migration SQL, PDF OCR, 질의 로그 DB 저장(chat_logs), 메트릭 수집기(Prometheus), 운영 배포

## 4. 패키지 구조

```text
com.example.ragassistant
├── RagAssistantApplication
├── chunk
│   └── Chunker
├── faq
│   └── FaqCatalog
├── config
│   ├── AppConfig
│   ├── OllamaProperties
│   ├── RagProperties
│   ├── RerankerProperties   (rag.reranker.*, TEI)
│   ├── AgentProperties      (agent.max-steps·max-tool-calls·timeout-ms·provider-order·max-history-turns·read-*·summarize-max-chars)
│   └── OpenApiConfig
├── controller
│   ├── HealthController
│   ├── DebugController
│   ├── DocumentController
│   ├── ChatController
│   ├── AgentController        (POST /api/agent, 동기)
│   └── AgentStreamController  (POST /api/agent/stream, SSE)
├── dto
│   ├── DocumentResponse
│   ├── DocumentListResponse
│   ├── ChunkResponse
│   ├── ChatRequest
│   ├── ChatResponse
│   ├── ChatStreamEvent
│   ├── SourceCitation
│   ├── AgentRequest          (message·provider·messages[] 멀티턴)
│   ├── AgentResponse
│   ├── AgentStep
│   ├── ConversationTurn       (멀티턴 1턴: role·content)
│   └── ErrorResponse
├── domain
│   ├── Document
│   ├── Chunk
│   ├── StoredChunk
│   └── SearchHit
├── parser
│   └── DocumentParser
├── repository
│   ├── DocumentRepository
│   ├── ChunkRepository
│   └── EmbeddingRepository
├── llm
│   ├── ChatModelClient        (chat·streamChat 경계, 구현 비의존)
│   ├── EmbeddingModelClient   (embed 경계 — RAG 정책 미포함)
│   └── agent
│       ├── AgentChatClient            (tool calling 경계 — ChatModelClient와 분리)
│       ├── OpenAiCompatAgentClient    (Groq 등 OpenAI 호환 tool calling)
│       ├── OllamaAgentClient          (Ollama tool calling)
│       ├── RoutingAgentChatClient     (@Primary, provider 라우팅·폴백)
│       └── AgentMessage / ToolCall / ToolSpec / AgentTurn (전송 모델)
├── agent
│   ├── AgentOrchestrator        (멀티스텝 루프·안전장치·grounded 판정·메모리 시드·스트리밍)
│   ├── AgentStreamHandler       (스트리밍 이벤트 싱크: onToolCall·onToolResult·onDelta, NOOP=동기)
│   └── tool
│       ├── AgentTool / ToolResult / ToolRegistry
│       ├── SearchDocumentsTool   (Retriever.retrieve() 래핑)
│       ├── ListDocumentsTool     (DocumentService 목록)
│       ├── ReadDocumentTool      (ChunkService 본문 청크 범위)
│       └── SummarizeDocumentTool (DocumentService 본문 + LLM 요약)
├── search
│   ├── RrfFusion
│   └── Reranker            (TEI cross-encoder /rerank, 2단계 retrieval)
├── observability
│   ├── QueryTelemetry          (요청 1건 지표 POJO)
│   ├── QueryTelemetryContext   (ThreadLocal 수집기 + 구조적 로그)
│   ├── NoAnswerReason          (EMPTY_HITS | LLM_NO_ANSWER)
│   └── RequestIdFilter         (MDC requestId + X-Request-Id 헤더)
├── eval
│   ├── RagEvalRunner       (ApplicationRunner, --rag.eval.enabled)
│   ├── EvalMode            (RAG_ON | RAG_OFF)
│   ├── EvalQuestion        (questions.json 1문항)
│   ├── EvalQuestionSet     (questions.json 로더)
│   ├── EvalScorer          (룰 기반 채점)
│   ├── EvalResult / EvalReport
│   └── EvalReportWriter    (eval/reports/ JSON·MD)
├── service
│   ├── OllamaService       (implements ChatModelClient·EmbeddingModelClient — 단일 LLM 구현)
│   ├── DocumentService
│   ├── ChunkService
│   ├── EmbeddingService
│   ├── VectorStoreService
│   ├── FaqIndexService
│   ├── FaqBootstrap
│   ├── Retriever
│   ├── PromptBuilder
│   ├── HealthService       (ollama·db·reranker readiness)
│   └── RagService
└── exception
    ├── GlobalExceptionHandler
    ├── EmptyFileException
    ├── UnsupportedDocumentFormatException
    ├── OllamaUnavailableException
    ├── OllamaResponseException
    ├── DocumentNotFoundException
    ├── DuplicateDocumentException
    └── DocumentParseException
```

## 5. API 설계

### Health
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/health` | 앱 + 의존성(ollama·database·reranker) 상태. `DOWN`이면 503 (§12) |

### Debug (개발용, `local` 프로필)
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/debug/ollama/chat` | Ollama chat 테스트 |
| GET | `/api/debug/ollama/embed` | Ollama embedding 테스트 |
| GET | `/api/debug/documents/{id}/chunks` | 문서 chunk 분할 결과 |
| GET | `/api/debug/retrieval/compare?q=` | vector-only vs hybrid 검색 비교 |

### Document
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/documents/upload` | 문서 업로드 + 인덱싱 (동일 파일명 → 409) |
| GET | `/api/documents` | 문서 목록 |
| DELETE | `/api/documents/{id}` | 문서 삭제 (204) |

### Chat
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/chat` | RAG 질의응답 (answer, sources, grounded) |
| POST | `/api/chat/stream` | RAG 스트리밍 (SSE) |

### Chat Response (`POST /api/chat`)

응답 필드: `answer`(생성된 답), `sources`(출처 목록 — `documentName`·`chunkId`·`snippet`·`score`), `grounded`(근거 기반 여부). 근거가 없으면 `answer`는 no-answer 문구, `sources`는 빈 배열, `grounded`는 false.

### Chat Streaming (`POST /api/chat/stream`)

요청 body는 `/api/chat`과 동일 (`{"question":"..."}`). 빈 질문은 스트림 열기 전 **400 JSON** (`GlobalExceptionHandler`).

| SSE event | data (JSON) | 설명 |
|---|---|---|
| `delta` | `{"text":"..."}` | LLM 토큰(청크) 단위 |
| `done` | `{"answer":"...","sources":[...],"grounded":true}` | 최종 응답 (`ChatResponse`와 동일 필드) |

- retrieve·no-answer 판정은 스트림 시작 전 동기 처리 (hits empty → `done`만 no-answer)

### Error Response

`ErrorResponse` 형식: `error`(코드)·`message`(설명) 두 필드. HTTP 상태·error 코드·no-answer 등 전체 매핑은 **§9 예외 / 상태 처리** 참고.

### Agent (tool calling)

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/agent` | tool calling 에이전트(동기). LLM이 도구를 호출해 멀티스텝으로 답 구성 |
| POST | `/api/agent/stream` | 에이전트 스트리밍(SSE). 도구 호출/결과 `step` + 최종 답 `delta` (§18 in [`DECISIONS.md`](DECISIONS.md)) |

요청: `{ "message": "...", "provider": "groq", "messages": [...] }` — `provider`·`messages`는 선택. `messages`는 이전 대화(무상태 멀티턴, 서버가 `agent.max-history-turns`로 최근 N턴만 사용). 미지정 시 단발.

도구: `search_documents`(검색 = `Retriever.retrieve()` 래핑), `list_documents`(문서 목록), `read_document`(특정 문서 본문을 청크 범위로 — `ChunkService`), `summarize_document`(문서 전체 기반 LLM 요약). 본문 메타 단건 조회 `get_document`는 소형 모델의 환각 id 호출을 유발해 제거(§17 in [`DECISIONS.md`](DECISIONS.md)).

응답 필드 (`POST /api/agent`): `answer`·`sources`·`grounded`(여기까지 `/api/chat`과 동일) + `provider`(사용한 LLM)·`stopReason`·`steps`(도구 호출 추적).

- `stopReason`: `FINAL` | `MAX_STEPS` | `TIMEOUT` | `NO_PROVIDER` | `ERROR`.
- `steps`: tool 호출 추적(투명성·디버깅). 빈 배열 = 도구 없이 직답.
- 안전장치: `agent.max-steps`(모델 턴 상한, 기본 5)·`agent.max-tool-calls`(도구 호출 총량 상한, 기본 10)·`agent.timeout-ms`. 상한 소진 시 **도구 없이 1회 강제 종결**(런어웨이 차단, §18). tool 오류·잘못된 인자는 텍스트로 모델에 되먹여 복구(스텝 1 소모).
- grounded/no-answer 판정은 `/api/chat`과 동일 철학(근거 없으면 `sources` 비움). transport는 `ChatModelClient`와 분리된 `AgentChatClient`로 회귀 0.

#### 스트리밍 (`POST /api/agent/stream`, SSE)

요청 body는 `/api/agent`와 동일. 빈 `message`는 스트림 열기 전 **400 JSON**.

| SSE event | data (JSON) | 설명 |
|---|---|---|
| `step` | `{"index":1,"phase":"tool_call","tool":"search_documents","arguments":{...}}` | 도구 호출 시작 |
| `step` | `{"index":1,"phase":"tool_result","tool":"search_documents","resultSummary":"8 sources"}` | 도구 결과 |
| `delta` | `{"text":"..."}` | 최종 답 토큰(청크) |
| `done` | `{"answer":"...","sources":[...],"grounded":true,"provider":"...","stopReason":"FINAL","steps":[...]}` | 최종 페이로드(`AgentResponse` 동일 필드) |
| `error` | `{"error":"...","message":"..."}` | 처리 중 오류 |

- 순서: `step`(tool_call→tool_result)* → `delta`* → `done`. 도구 호출은 생성 도중 실시간 방출, 최종 답은 생성 완료 후 조각(delta)으로 흘림(MVP; 진짜 토큰 스트리밍은 후속).
- 스트리밍은 **default leg 단독**(폴백 없음, `/api/chat/stream`과 동일 정책).

### Voice (WebSocket 콜봇)

| 종류 | 경로 | 설명 |
|---|---|---|
| WebSocket | `/ws/voice` | 음성 통화 게이트웨이(양방향). 연결 시 `call_session` 생성, 종료 시 `final_state`·`ended_at` 기록 |

브라우저가 Web Speech API로 STT를 수행하므로 오디오가 아닌 **인식 텍스트**를 전송한다. 서버는 tool calling agent(`AgentOrchestrator.runStreaming`)로 답을 만든다. 세션 단위 대화 이력을 함께 넘겨 **멀티턴**(후속 질문 맥락 유지)을 지원하고, 검색은 agent가 `search_documents` 도구로 수행한다. TTS는 Google → 브라우저 폴백.

클라이언트 → 서버 (JSON):

| type | payload | 설명 |
|---|---|---|
| `user_utterance` | `{ "text": "...", "sttMs": 1234 }` | 최종 인식 텍스트 + 브라우저 측정 STT 지연 |

서버 → 클라이언트 (JSON `VoiceEvent`):

| type | payload | 설명 |
|---|---|---|
| `state` | `{ "state": "THINKING" }` | 상태 전이(`IDLE`/`LISTENING`/`THINKING`/`SPEAKING`/`HANDOFF`) |
| `answer.delta` | `{ "text": "..." }` | agent 최종 답 조각(생성 완료 후 delta) |
| `answer.done` | `{ "answer": "...", "sources": [...], "grounded": true }` | 최종 답(`ChatResponse` 필드) |
| `handoff` | `{ "reason": "NO_ANSWER_X2" }` | no-answer 2회 연속 → 상담원 전환 |
| `tts.fallback` | `{ }` | Google TTS 미사용/실패 → 브라우저 TTS로 재생하라는 신호 |
| `error` | `{ "error": "...", "message": "..." }` | 처리 중 오류 |

- 인사말 등은 agent를 거치지 않고 정형 응답(canned)으로 가로채 동문서답을 막는다(`llm_ms=0`).
- 세션 대화 이력은 메모리에만 유지(통화 종료 시 소멸)하며, agent가 `agent.max-history-turns`로 최근 N턴만 사용한다.
- 턴 종료 시 `call_turns`에 마스킹된 사용자 텍스트·답변·구간 지연을 저장한다(§6).
- 로그 저장 실패는 통화를 막지 않는다(예외 흡수).

## 6. DB 설계 (pgvector)

### documents (migration SQL은 repo 미포함)
| column | type | note |
|---|---|---|
| id | bigserial PK | |
| name | varchar | 원본 파일명 |
| content_type | varchar | mime/type |
| content | text | 파싱된 전체 텍스트 |
| created_at | timestamp | |

### document_chunks (수동 SQL)
| column | type | note |
|---|---|---|
| id | bigserial PK | |
| document_id | bigint FK → documents **ON DELETE CASCADE** | |
| chunk_index | int | |
| content | text | chunk 본문 |

### document_embeddings (수동 SQL)
| column | type | note |
|---|---|---|
| id | bigserial PK | |
| chunk_id | bigint FK → document_chunks **ON DELETE CASCADE** | |
| embedding | vector(768) | pgvector, `nomic-embed-text` |
| document_name | varchar | 검색 결과 표시용 |

### 인덱스
- `document_embeddings.embedding` → HNSW (`vector_cosine_ops`)
- `document_chunks.content` → GIN (`gin_trgm_ops`) — hybrid lexical leg
- `document_chunks.document_id`
- `documents.created_at`

### Extension (hybrid, 로컬 수동)
- `pg_trgm` — `ChunkRepository.searchLexical` (`similarity(content, ?)`)

### call_sessions / call_turns (Voice 통화 로그)
> 통화 로그만 `src/main/resources/schema.sql`로 앱 시작 시 자동 생성한다(`spring.sql.init.mode=always` + `CREATE TABLE IF NOT EXISTS`). 기존 documents/chunks/embeddings는 수동 DDL 유지.

**call_sessions**

| column | type | note |
|---|---|---|
| id | bigserial PK | |
| started_at / ended_at | timestamp | |
| final_state | varchar(20) | COMPLETED / HANDOFF / ERROR |
| handoff_reason | varchar(50) null | no-answer 연속 등 전환 사유 |

**call_turns**

| column | type | note |
|---|---|---|
| id | bigserial PK | |
| session_id | bigint FK → call_sessions **ON DELETE CASCADE** | `idx_call_turns_session` |
| turn_index | int | 턴 순번 |
| user_text_masked | text | **PII 마스킹 후** 저장 (`PiiMasker`) |
| answer_text | text | |
| grounded | boolean | |
| stt_ms / llm_ms / tts_ms / ttfb_ms | int | 구간별 지연 |
| created_at | timestamp | |

**PII 마스킹 (`PiiMasker`)**
- 대상: 이메일·주민등록번호·카드번호·휴대전화·긴 숫자열(계좌 등).
- 순서: 구체 패턴(이메일→주민→카드→전화)을 먼저 치환해 의미 토큰(`[전화번호]` 등)을 확정한 뒤, 남은 `\d{7,}`를 `[번호]`로 일반화한다. 일반화를 먼저 하면 구체 토큰을 잃기 때문.

**구간 지연 (`call_turns`)**
- `llm_ms`(RAG+LLM 스트림), `tts_ms`(TTS 합성), `ttfb_ms`(발화 처리~첫 `answer.delta`)는 서버 측정.
- `stt_ms`는 브라우저 STT가 측정해 `user_utterance`로 전달한다.
- 로그 저장 실패는 통화를 막지 않는다(세션 생성/턴 저장/종료 기록 모두 예외 흡수).

## 7. 설정값

`application.yml`

```yaml
ollama:
  base-url: http://localhost:11434
  chat-model: qwen2.5:7b
  embedding-model: nomic-embed-text
  temperature: 0

rag:
  chunk-size: 450
  chunk-overlap: 150
  top-k: 10
  min-score: 0.2
  embedding-dimension: 768
  # hybrid search
  hybrid-enabled: true
  lexical-top-k: 10
  lexical-min-score: 0.1
  rrf-k: 60
  # rerank (2단계 retrieval, §14 DECISIONS)
  rerank-enabled: true
  candidate-top-k: 30       # rerank 전 후보 폭 (vector·lexical 공통)
  rerank-top-n: 8           # rerank 후 context 개수
  reranker:
    base-url: http://localhost:8085   # TEI 컨테이너
    model: BAAI/bge-reranker-v2-m3
    timeout-ms: 5000        # 초과 시 fallback(원본 순서)

agent:                      # tool calling 에이전트 (§17·§18 DECISIONS)
  max-steps: 5              # 모델 턴 상한
  max-tool-calls: 10        # 도구 호출 총량 상한(런어웨이 방지) → 초과 시 도구 없이 강제 종결
  timeout-ms: 180000
  provider-order: [groq, ollama]
  max-history-turns: 6      # 멀티턴 메모리 상한(최근 N턴)
  read-max-chunks: 6        # read_document 1회 청크 수
  read-max-chars: 4000      # read_document 본문 길이 상한
  summarize-max-chars: 8000 # summarize_document 본문 예산
```

TEI reranker는 별도 컨테이너(예: `dietManagement` docker-compose의 `tei-reranker`, 포트 8085)로 띄운다. 미실행이어도 `rerank-enabled: true`면 fallback으로 RAG는 동작한다(품질만 저하).

DB URL·username은 `application.yml`, **비밀번호 등 머신별 값**은 `application-local.yml` (gitignore, profile `local`).

Ollama·RAG 기본값은 **`application.yml`만** 관리한다. `application-local.yml`에 `ollama` 블록을 두면 local 프로필 활성 시 **덮어써져** 테스트·모델 변경이 헷갈릴 수 있다.

## 8. 프롬프트 정책 (요약)

프롬프트는 system·user 두 레이어로 구성된다(`OllamaService`·`PromptBuilder`). 핵심 원칙은 검색으로 찾은 근거(Context)만 사용해 한국어로 답하고, 근거가 없으면 "문서에서 확인할 수 없는 질문입니다"로 응답하는 것이다. LLM 응답은 후처리(`RagService.sanitizeLlmAnswer`)로 메타 잔여물을 제거하고, no-answer 패턴이면 `grounded=false`·빈 `sources`로 정렬한다. 검색 hit이 없으면 LLM을 호출하지 않고 바로 no-answer를 반환한다(§9).

## 9. 예외 / 상태 처리

| 상황 | HTTP | error / 동작 |
|---|---|---|
| Ollama 미실행·연결 실패 | 503 | `OLLAMA_UNAVAILABLE` |
| Ollama HTTP 4xx/5xx·응답 형식 오류 | 502 | `OLLAMA_RESPONSE_ERROR` |
| DB 연결·쿼리 실패 | 503 | `DATABASE_UNAVAILABLE` (상세는 서버 로그) |
| 업로드 파일 없음 | 400 | `EMPTY_FILE` |
| 지원하지 않는 포맷 | 400 | `UNSUPPORTED_FORMAT` |
| 동일 파일명 재업로드 | 409 | `DUPLICATE_DOCUMENT` |
| 삭제 대상 문서 없음 | 404 | `DOCUMENT_NOT_FOUND` |
| 검색 hit 없음 / min-score 미달 | 200 | `grounded=false`, no-answer (LLM 미호출) |
| TEI reranker 실패·타임아웃 (`rerank-enabled: true`) | 200 | fallback: 원본 검색 순서 + `rerank-top-n` 컷, RAG 정상 (품질만 저하) |
| LLM no-answer 패턴 (한국어) | 200 | `grounded=false`, sources `[]` (`NO_ANSWER_MESSAGE` 동일 또는 no-answer로 시작 + 짧은 꼬리 ≤40자) |
| 빈 질문 | 400 | `BAD_REQUEST` |

**알려진 한계:** PDF 스캔본은 OCR 미지원(400). hybrid 기본 on; 한국어 형태소 FTS 없음·lexical은 trigram 1차. rerank on 시 출처 `score`는 cosine이 아닌 **rerank score**(cross-encoder 스케일, 음수 가능); rerank off면 vector cosine. debug `/api/debug/retrieval/compare`는 의도적으로 **rerank 전** 후보를 보여줌. RAG off 시 한국어 가드는 PromptBuilder보다 약함(`RAG_EVAL_v2.md`). hybrid는 [`DECISIONS.md`](DECISIONS.md) §11, rerank는 §14.

## 10. 외부 의존성

| 구성요소 | 역할 | 비고 |
|---|---|---|
| Ollama (Docker) | chat + embedding | 기존 인스턴스 공유 |
| PostgreSQL | metadata + vector | pgvector extension 필요 |
| TEI reranker (Docker) | cross-encoder `/rerank` | `bge-reranker-v2-m3`, 포트 8085, 선택(없으면 fallback) |
| Browser/UI | upload + chat | `static/index.html` |

## 11. RAG 평가 자동화

고정 질문 세트로 RAG 품질을 반복 측정한다. REST가 아니라 `RagService`/`OllamaService`를 **직접 호출**해 직렬화·네트워크 변수를 제거한다 (설계 이유는 [`DECISIONS.md`](DECISIONS.md) §13).

### 실행

```bash
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_OFF"
```

- `--rag.eval.enabled` 없으면 `RagEvalRunner`는 즉시 return (일반 bootRun·UI에 영향 없음)
- `--rag.eval.mode` 기본값 `RAG_ON`; `--rag.eval.questions`로 질문 파일 경로 override (기본 `eval/questions.json`)
- `@Order(100)` — `FaqBootstrap`(FAQ·코퍼스 인덱싱) 이후 실행

### 모드

| 모드 | 경로 | 비고 |
|---|---|---|
| `RAG_ON` | `RagService.chat` (검색 → no-answer/prompt → LLM) | 프로덕션 경로 |
| `RAG_OFF` | `OllamaService.chat` (검색·Context 없음) | v2 대조군(ablation), `grounded=false`·`sources=[]` 고정 |

### 채점 (`EvalScorer`)

LLM-as-judge 없이 **결정적 룰**로 0/1/2점. 문항 정의(`eval/questions.json`)의 필드만 본다.

| 필드 | 의미 |
|---|---|
| `expectNoAnswer` | 문서 밖 질문 — `grounded=false`·`sources=[]`·no-answer 문구여야 만점 |
| `mustGrounded` / `minSourceCount` | 사실·정책 문항의 grounded·출처 하한 |
| `mustContainAll` / `mustContainAny` | 기대 키워드 (전부 / 일부) |
| `mustNotContain` | 금지·환각 키워드 (위반 시 0점, `failReasons` 기록) |

### 산출물 (`eval/reports/`)

| 파일 | Git | 용도 |
|---|---|---|
| `latest-rag-on.{json,md}`, `latest-rag-off.{json,md}` | 커밋 | 모드별 최신 결과 |
| `compare-latest.md` | 커밋 | on/off 점수·Δ 비교 (둘 다 실행 시 갱신) |
| `runs/{timestamp}_{MODE}.{json,md}` | gitignore | 실행 이력 (로컬 튜닝용) |

질문 세트(`eval/questions.json`)가 SSOT다. 최신 자동 실행 결과·해석은 `RAG_EVAL_v2.md`.

## 12. 운영 관측성 (요청 로그 · Health)

실험(eval)에서 검증된 파이프라인이 운영에서도 안정적으로 동작하는지 보기 위한 최소 관측성. 별도 인프라(메트릭 수집기·로그 DB) 없이 **구조적 로그 + Health**만 둔다.

### 요청 상관관계 (requestId)

- `RequestIdFilter`(`OncePerRequestFilter`, 최우선)가 요청마다 `requestId`를 MDC에 넣고 응답 헤더 `X-Request-Id`로 돌려준다.
- 클라이언트가 보낸 `X-Request-Id`가 있으면 재사용, 없으면 생성(8자).
- 스트리밍은 `ChatController`가 별도 스레드에서 처리하므로 `requestId`를 그 스레드 MDC로 전파한다.

### 구조적 요청 로그 (`rag.telemetry`)

`QueryTelemetryContext`(ThreadLocal)가 RAG 요청 1건의 지표를 모아 **INFO 한 줄**로 남긴다. 질문 원문·답변은 남기지 않는다(지표만 → PII 회피). 활성 컨텍스트가 없으면(eval runner 등) no-op.

```
query requestId=ab12cd34 hits=8 topScore=0.8312 grounded=true noAnswer=- embedMs=42 retrieveMs=15 rerankMs=120 genMs=2100 totalMs=2290 rerankFallback=false
```

| 필드 | 의미 | 기록 위치 |
|---|---|---|
| `requestId` | 요청 상관관계 (헤더와 동일) | `RequestIdFilter` → MDC |
| `hits` / `topScore` | LLM에 넘긴 context 수 / 1위 점수(rerank on이면 rerank score) | `RagService` |
| `grounded` / `noAnswer` | 근거 기반 여부 / no-answer 사유(`EMPTY_HITS`·`LLM_NO_ANSWER`·`-`) | `RagService` |
| `embedMs` / `retrieveMs` / `rerankMs` / `genMs` | 단계별 지연(임베딩·후보검색·rerank·LLM 생성) | `Retriever`·`RagService` |
| `totalMs` | 요청 전체(begin→end) | `QueryTelemetryContext` |
| `rerankFallback` | TEI 실패로 fallback 탔는지 (`-`=rerank 미수행) | `Reranker` |

- `embedMs`/`retrieveMs`/`rerankMs`로 병목이 임베딩·검색·rerank·LLM 중 어디인지 분해된다.
- `rerankFallback=true`는 TEI 다운 시 degraded 동작을 그대로 드러낸다(§14 DECISIONS의 fallback과 정합).

### Health 심화 (`GET /api/health`)

`HealthService`가 의존성 readiness를 짧은 타임아웃으로 점검한다.

| 의존성 | 점검 | 비고 |
|---|---|---|
| `database` | `Connection.isValid(2)` | core |
| `ollama` | `GET {base}/api/tags` 2xx | core |
| `reranker` | `GET {base}/health` 2xx | rerank off면 `DISABLED`(상태 무관) |

```json
{ "status": "DEGRADED", "service": "rag-assistant",
  "dependencies": { "ollama": "UP", "database": "UP", "reranker": "DOWN" } }
```

| status | 조건 | HTTP |
|---|---|---|
| `UP` | core UP + (reranker UP 또는 DISABLED) | 200 |
| `DEGRADED` | core UP + reranker DOWN | 200 |
| `DOWN` | database 또는 ollama DOWN | 503 |

reranker DOWN을 `DOWN`이 아닌 `DEGRADED`로 둔 건, fallback으로 RAG가 계속 동작하기 때문이다.
