# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | router |
| ranAt | 2026-06-20 00:13 |
| avgLatencyMs | 64923 |
| total | **17 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 2/2 | true | 8 | false |
| 4 | A_FACT | 2/2 | true | 8 | false |
| 5 | B_POLICY | 2/2 | true | 8 | false |
| 6 | B_POLICY | 1/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 9 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 10 | C_NO_ANSWER | 0/2 | true | 8 | false |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
- Spring Boot
- Ollama
- PostgreSQL pgvector

`RAG EVAL_v1.md`, `RAG_EVAL_v1.1.md`, `RAG_EVAL_v2.md` 문서에 따르면, 이 프로젝트는上述内容是中文，需要翻译成韩语并按照指令格式回答。以下是翻译后的答案：

```korean
- 스프링 부트
- 올라마
- PostgreSQL pgvector

RAG EVAL_v1.md, RAG_EVAL_v1.1.md, RAG_EVAL_v2.md 문서에 따르면, 이 프로젝트는 위 기술들을 사용하고 있습니다.
```
```

### 2. Ollama base-url은?

**score:** 2/2

```
`ollama`에 연결할 수 없습니다. Docker/서비스 실행 및 base-url(http://localhost:11434)을 확인하세요.

### 로컬 DDL (repo migration 미포함)

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_document_chunks_content_trgm
    ON document_chunks USING gin (content gin_trgm_ops);
```

### 검증 (2026-06, 수동)

| 항목 | 결과 |
| --- | --- |
| hybrid off 회귀 | 「Ollama base-url은?」— DECISIONS 1순위, `grounded: true` |
| hybrid on 회귀 | 동일 질문 — 순위·답변 동일 (vector가 이미 충분) |
| no-answer 회귀 | 「2025년 매출」— no-answer, `sources: []` |

[출처: ARCHITECTURE.md]
ection.isValid(2)` | core |
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

[출처: ARCHITECTURE.md]
e`는 TEI 다운 시 degraded 동작을 그대로 드러낸다(§14 DECISIONS의 fallback과 정합).

### Health 심화 (`GET /api/health`)

`HealthService`가 의존성 readiness를 짧은 타임아웃으로 점검한다.

| 의존성 | 점검 | 비고 |
|---|---|---|
| `database` | `Connection.isValid(2)` | core |
| `ollama` | `GET {base}/api/tags` 2xx | core |
| `reranker` | `GET {base}/health` 2xx | rerank off면 `DISABLED`(상태 무관) |

[출처: ARCHITECTURE.md]
. Docker/서비스 실행 및 base-url(http://localhost:11434)을 확인하세요.

### DB 설계 (pgvector)

| column | type | note |
|---|---|---|
| id | bigserial PK | |
| name | varchar | 원본 파일명 |
| content_type | varchar | mime/type |
| content | text | 파싱된 전체 텍스트 |
| created_at | timestamp | |

[출처: ARCHITECTURE.md]
mode` 기본값 `RAG_ON`; `--rag.eval.questions`로 질문 파일 경로 override (기본 `eval/questions.json`)
- `@Order(100)` — `FaqBootstrap`(FAQ·코퍼스 인덱싱) 이후 실행

### 모드

| 모드 | 경로 | 비고 |
|---|---|---|
| `RAG_ON` | `RagService.chat` (검색 → no-answer/prompt → LLM) | 프로덕션 경로 |
| `RAG_OFF` | `OllamaService.chat` (검색·Context 없음) | v2 대조군(ablation), `grounded=false`·`sources=[]` 고정 |

### 채점 (`EvalScorer`)

`sanitizeLlmAnswer`: `[Answer]`·`[no-answer]`·마지막 줄 `Context에서/Context에/Context만` 메타 잔여물 제거
- `isGrounded`: `NO_ANSWER_MESSAGE`와 동일 → `grounded=false`; no-answer로 **시작** + 짧은 꼬리(중국어 등, +40자 이내)도 no-answer로 간주

검색 hit 없음은 LLM 호출 전 no-answer — §9.

## 10. 예외 / 상태 처리

| 상황 | HTTP | error / 동작 |
|---|---|---|
| Ollama 미실행·연결 실패 | 503 | `OLLAMA_UNAVAILABLE` |
```

### 3. chat / embedding model 이름은?

**score:** 2/2

```
이 문서는 RAG(Question Answering System) 프로젝트에서 내린 주요 설계 및 기술 선택과 그 이유를 정리합니다.

### 1. 프로젝트 목표

* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.
* 단순 챗 UI가 아니라 검색 + 생성 + 출처 인용 파이프라인이 핵심
* 외부 LLM API 없이 로컬 환경에서 동작

### 2. LLM / Embedding: Ollama

* 선택
	+ Docker로 이미 실행 중인 Ollama 인스턴스 공유
	+ Base URL: `http://localhost:11434`
	+ Chat model: `qwen2.5:7b`
	+ Embedding model: `nomic-embed-text`

### 3. 벡터 저장소: PostgreSQL + pgvector

* 선택
	+ Chroma 대신 **이미 설치된 PostgreSQL + pgvector** 사용
* 이유
	+ 별도 벡터 DB를 추가하지 않아도 됨
	+ Spring Boot + JDBC/JPA와 자연스럽게 연결
	+ 문서 메타데이터와 벡터를 한 DB에서 관리 가능
	+ 메타데이터 CRUD·벡터 검색을 같은 트랜잭션/연결 흐름으로 다루기 쉬움

### 4. API / UX 정책

* 응답 정책
	+ 검색된 context만 근거로 답변
	+ context가 없거나 score가 낮으면 **no-answer**
	+ 가능하면 출처 문서명 / snippet 포함
	+ API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일
* 초기 UI
	+ 먼저 API 완성
	+ 이후 간단한 업로드 + 채팅 UI 추가

### 5. Chunking 정책 (2026-06)

*Chunking은 RAG의 핵심이기 때문에 chunking 정책을 carefully 설계해야 함.
*chunk_size: 450, chunk_overlap: 150
*top-k: 10, min-score: 0.2
*embedding-dimension: 768

### 6. LLM 호출 경계 (2026-06)

* Ollama 호출을 `llm.ChatModelClient`(chat·streamChat)·`llm.EmbeddingModelClient`(embed) **인터페이스 뒤로 분리**. 소비자(`RagService`·`EmbeddingService`·`RagEvalRunner`·`DebugController`)는 인터페이스에만 의존.
* 구현체는 **Ollama 단일**(`OllamaService implements ChatModelClient, EmbeddingModelClient`) — 기존 "외부 LLM API 없이 로컬 실행"(§2) 결정 유지. SaaS·라우팅을 **지금 붙이지 않음**.

### 7. 벡터 저장소: PostgreSQL + pgvector

* 선택
	+ Chroma 대신 **이미 설치된 PostgreSQL + pgvector** 사용
* 이유
	+ 별도 벡터 DB를 추가하지 않아도 됨
	+ Spring Boot + JDBC/JPA와 자연스럽게 연결
	+ 문서 메타데이터와 벡터를 한 DB에서 관리 가능
	+ 메타데이터 CRUD·벡터 검색을 같은 트랜잭션/연결 흐름으로 다루기 쉬움

### 8. API / UX 정책 (2026-06)

* 응답 정책
	+ 검색된 context만 근거로 답변
	+ context가 없거나 score가 낮으면 **no-answer**
	+ 가능하면 출처 문서명 / snippet 포함
	+ API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일

### 9. 초기 UI (2026-06)

* 먼저 API 완성
* 이후 간단한 업로드 + 채팅 UI 추가
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 2/2

```
`DECISIONS.md`에 따르면, pgvector 대신 Chroma를 사용하지 않은 이유는 별도 벡터 DB를 추가하지 않아도 됨, Spring Boot + JDBC/JPA와 자연스럽게 연결 가능, 그리고 문서 메타데이터와 벡터를 한 DB에서 관리할 수 있기 때문입니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
Spring AI를 전면 도입하지 않은 이유는 chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어하기 위함입니다. 또한, Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확해야 함으로써 관리의 용이성을 높이기 위함입니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 1/2

```
검색 hit가 없을 때 앱은 no-answer로 동작합니다. `RagService`에서 검색-hit가 없는 경우 `done` 응답으로 no-answer를 반환합니다.
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
chunk-size는 450, chunk-overlap는 150로 설정되어 있다.
```

### 8. 이 프로젝트의 2025년 매출은?

**score:** 2/2

```
문서에서 확인할 수 없는 질문입니다.
```

### 9. Kubernetes로 배포했나?

**score:** 2/2

```
문서에서 확인할 수 없는 질문입니다.
```

### 10. fine-tuning을 했나?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
이 질문에 대한 근거가 없으면 "문서에는 ○○에 대한 내용이 없습니다."로 짧게 말하세요.

```bash
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

## 9. 예외 처리·문서 정책

### Ollama 오류
- `OllamaService.postForMap()`: chat/embed 공통 HTTP 호출
- 연결 실패 (`ResourceAccessException`) → 503 `OLLAMA_UNAVAILABLE`
```
