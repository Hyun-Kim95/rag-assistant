# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | ollama-1b |
| ranAt | 2026-06-19 23:52 |
| avgLatencyMs | 6098 |
| total | **10 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 2/2 | true | 8 | false |
| 4 | A_FACT | 0/2 | true | 8 | false |
| 5 | B_POLICY | 1/2 | true | 8 | false |
| 6 | B_POLICY | 1/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 0/2 | true | 8 | false |
| 9 | C_NO_ANSWER | 0/2 | true | 8 | false |
| 10 | C_NO_ANSWER | 0/2 | true | 8 | false |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
이 프로젝트의 기술 스택은 다음과 같습니다.

1. Spring Boot
2. Ollama
3. PostgreSQL
4. RAG (Rapid Application Growth)
5. RESTful API
6. Chat Embedding
7. LLM (Large Language Model)

이 프로젝트는 Ollama를 사용하여 chat(embed) functionality을 제공하는 것을 목표로 합니다. Ollama는 대조군과 함께 chat(embed) functionality을 제공할 수 있습니다.

 Spring Boot와 PostgreSQL를 사용하여 데이터베이스를 구축하고, RAG를 사용하여 API를 개발하고, RESTful API를 구축하여 chat(embed) functionality을 제공합니다.

 Chat Embedding은 Ollama를 사용하여 chat(embed) functionality을 제공하는 것을 목표로 합니다. Ollama는 chat(embed) functionality을 제공할 수 있습니다.

 LLM은 대조군과 함께 chat(embed) functionality을 제공할 수 있습니다. LLM는 large language model이지만, chat(embed) functionality을 제공할 수 있습니다.

 Spring AI 미사용으로, Ollama를 사용하여 chat(embed) functionality을 제공하는 것을 목표로 합니다.

 RAG 파이프라인은 chunking · retrieval · prompt · no-answer의 구현을 포함합니다. chunking은 API call을 제어하고, retrieve는 데이터베이스에서 데이터를 가져오기 위한 API call을 제어하고, prompt은 chat(embed) functionality을 제공하기 위한 API call을 제어하고, no-answer은 api call이 실패할 때 no-answer의 api call을 제어합니다.

 Ollama 호출은 RESTful API를 구축하여 chat(embed) functionality을 제공하는 것을 목표로 합니다. Ollama 호출은 chat(embed) functionality을 제공할 수 있습니다.

 Spring AI 미사용으로, Ollama 호출을 RESTful API에 연결하고, chat(embed) functionality을 제공하는 것을 목표로 합니다.

 LLM 호출 경계는 Ollama 호출을 `llm.ChatModelClient` (chat·streamChat) · `llm.EmbeddingModelClient` (embed) **인터페이스 뒤로 분리**합니다.
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

Service` sanitize와 정합).

### LLM 응답 후처리 (`RagService`)

- `sanitizeLlmAnswer`: `[Answer]`·`[no-answer]`·마지막 줄 `Context에서/Context에/Context만` 메타 잔여물 제거
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

**score:** 0/2

```
`Reranker`가 `pgvector` 대신 `chroma`를 사용하는 이유는 `RAG` (Ranking Algorithm)에서 `chroma`의 특성 (`similarity()`와 `chunk`을 사용하여 유사도 계산)을 사용하기 때문입니다. 

`RAG`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다. `chroma`는 `nomic-embed-text` API를 통해 `vector` 형식의 embedding을 생성할 수 있습니다. 

`RAG`에서 `chroma`를 사용하는 이유는 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있습니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `similarity()`는 `nomic-embed-text` API에서 `vector` 형식의 embedding을 생성할 때 사용됩니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `chunk`을 사용하여 유사도 계산을 할 수 있기 때문입니다. `chunk`은 `pgvector`와 `chroma`를 사용하여 유사도 계산을 할 수 있습니다.

`RAG`의 특성은 `similarity()`과 `chunk`을
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 1/2

```
Spring AI를 전면 도입하지 않은 이유는 다음과 같습니다.

1.  **Lack of RAG (Reranking Algorithm)**: RAG은 LLM의 score를 조정하여 원본 search를 개선하는 방법입니다. However, RAG가 구현되지 않았기 때문에 LLM의 score가 정상적인 수준으로 유지되지 않았습니다.
2.  **No-answer Policy**: No-answer policy는 LLM가 원본 search에 대한 정보를 제공하지 않으면 no-answer를 return하는 방식입니다. However, Spring AI가 RAG를 구현하지 않았기 때문에 LLM의 score가 정상적인 수준으로 유지되지 않았습니다.
3.  **Outdated Architecture**: Architecture는 2026-06에 cập nhật되었습니다. However, Spring AI가 RAG를 구현하지 않았기 때문에 architecture가 outdated remained습니다.
4.  **No Source Citation**: Source Citation은 LLM의 score를 정상적인 수준으로 유지하는 방법입니다. However, Spring AI가 RAG를 구현하지 않았기 때문에 source citation이 absent remained.
5.  **No Error Handling**: Error handling은 LLM의 score가 정상적인 수준으로 유지되지 않았을 때 발생하는 오류를 handled하는 방법입니다. However, Spring AI가 RAG를 구현하지 않았기 때문에 error handling이 absent remained.

Spring AI를 전면 도입한 이유는 다음과 같습니다.

1.  **RAG (Reranking Algorithm) 구현**: RAG은 LLM의 score를 조정하여 원본 search를 개선하는 방법입니다. Spring AI가 RAG를 구현하면 LLM의 score가 정상적인 수준으로 유지되며, 원본 search의ประสิทธิภาพ이 향상됩니다.
2.  **No-answer Policy 구현**: No-answer policy는 LLM가 원본 search에 대한 정보를 제공하지 않으면 no-answer를 return하는 방식입니다. Spring AI가 RAG를 구현하면 LLM의 score가 정상적인 수준으로 유지되며, no-answer를 return하지 않습니다.
3.  **Outdated Architecture 구현**: Architecture는 2026-06에 cập nhật되었습니다. Spring AI가 RAG를 구현하면 architecture가 outdated remained이 아니라, architecture가 개선되었습니다.
4.  **Source Citation 구현**: Source Citation은 LLM의 score를 정상적인 수준으로 유지하는 방법입니다. Spring AI가 RAG를 구현하면 source citation이 absent remained이 아니라, source citation이 추가되었습니다.
5.  **Error Handling 구현**: Error handling은 LLM의 score가 정상적인 수준으로 유지되지 않았을 때 발생하는 오류를 handled하는 방법입니다. Spring AI가 RAG를 구현하면 error handling이Absent remained이 아니라, error handling이 개선되었습니다.

Spring AI를 전면 도입한 이유는 다음과 같습니다.

1.  **RAG (Reranking Algorithm) 구현**: RAG은 LLM의 score를 조정하여 원본 search를 개선하는 방법입니다. Spring AI가 RAG를 구현하면 LLM의 score가 정상적인 수준으로 유지되며, 원본 search의ประสิทธิภาพ이 향상됩니다.
2.  **No-answer Policy 구현**: No-answer policy는 LLM가 원본 search에 대한 정보를 제공하지 않으면 no-answer를 return하는 방식입니다. Spring AI가 RAG를 구현하면 LLM의 score가 정상적인 수준으로 유지되며, no-answer를 return하지 않습니다.
3.  **Outdated Architecture 구현**: Architecture는 2026-06에 cập nhật되었습니다. Spring AI가 RAG를 구현하면 architecture가 outdated remained이 아니라, architecture가 개선되었습니다.
4.  **Source Citation 구현**: Source Citation은 LLM의 score를 정상적인 수준으로 유지하는 방법입니다. Spring AI가 RAG를 구현하면 source citation이 absent remained이 아니라, source citation이 추가되었습니다.
5.  **Error Handling 구현**: Error handling은 LLM의 score가 정상적인 수준으로 유지되지 않았을 때 발생하는 오류를 handled하는 방법입니다. Spring AI가 RAG를 구현하면 error handling이Absent remained이 아니라, error handling이 개선되었습니다.

Spring AI를 전면 도입한 이유는 다음과 같습니다.

1.  **RAG (Reranking Algorithm) 구현**: RAG은 LLM의 score를 조정하여 원본 search를 개선하는 방법입니다. Spring AI가 RAG를 구현하면 LLM의 score가 정상적인 수준으로 유지되며, 원본 search의ประสิทธิภาพ이 향상됩니다.
2.  **No-answer Policy 구현**: No-answer
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 1/2

```
문서 업로드 후 RAG 질의·출처·no-answer 동작 (~45초 분량).

img src="https://github.com/user-attachments/assets/1e1830a6-3e13-4a6d-b255-7d9659e46fc5" />
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
chunk-size는 450, chunk-overlap는 150로 설정되어 있다.
```

### 8. 이 프로젝트의 2025년 매출은?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
`RAG_EVAL_v1.2.md`에서 `RagEvalRunner`가 `RagService.chat`를 직접 호출하는 이유는 `RAG_OFF`가 검색된 context만 근거로 답변하고, `no-answer` 사유가 없기 때문입니다.

`RAG_OFF`는 검색된 context가 없거나 score가 낮은 경우에만 사용됩니다. `no-answer` 사유가 없다면 `RagEvalRunner`는 `RagService.chat`를 직접 호출하여 LLM의 평가 결과를 얻습니다.

`RagEvalRunner`가 `RagService.chat`를 직접 호출하는 이유는 `RAG_OFF`이 검색된 context만 근거로 답변하고, `no-answer` 사유가 없기 때문입니다.
```

### 9. Kubernetes로 배포했나?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
문서에서 확인할 수 없는 질문입니다. 

```json
{
  "answer": "이 프로젝트는 Spring Boot와 Ollama를 사용합니다.",
  "sources": [
    {
      "documentName": "README.md",
      "chunkId": 12,
      "snippet": "Tech Stack: Spring Boot, Ollama, PostgreSQL pgvector",
      "score": 0.82
    }
  ],
  "grounded": true
}
```

이 질문에 답하는 근거가 [Context]에 전혀 없을 때:

1.  완전 없음 (`no-answer`): `"문서에서 확인할 수 없는 질문입니다."`
2.  부분만 있음 (`partial_answer`): `"이 프로젝트는 Spring Boot와 Ollama를 사용합니다."`
3.  질문이 모호함 (`unknown`): `[Context]에 여러 해석이 가능할 때, `RAG 스트리밍 (SSE)`으로 구체적으로 질문해 주세요.`

문서에는 다음과 같은 정보가 있습니다.

*   `RAGEval_v1.md`: RAG(eval)评估기능
*   `RAGEval_v2.md`: RAG(eval)评估기능
*   `RAGEval_v3.md`: RAG(eval)评估기능
*   `RAGEval_v4.md`: RAG(eval)评估기능

`RAGEval_v1.md`, `RAGEval_v2.md`, `RAGEval_v3.md`, `RAGEval_v4.md`는 RAG(eval)评估기능입니다.

`RAGEval_v1.1.md`, `RAGEval_v2.1.md`, `RAGEval_v3.1.md`, `RAGEval_v4.1.md`는 RAG(eval)评估기능의 버전입니다.

`RAGEval_v1.0.md`: RAG(eval)评估기능

`RAGEval_v1.2.md`: RAG(eval)评估기능

`RAGEval_v1.3.md`: RAG(eval)评估기능

`RAGEval_v1.4.md`: RAG(eval)评估기능

`RAGEval_v1.5.md`: RAG(eval)评估기능

`RAGEval_v1.6.md`: RAG(eval)评估기능

`RAGEval_v1.7.md`: RAG(eval)评估기능

`RAGEval_v1.8.md`: RAG(eval)评估기능

`RAGEval_v1.9.md`: RAG(eval)评估기능

`RAGEval_v1.10.md`: RAG(eval)评估기능

`RAGEval_v1.11.md`: RAG(eval)评估기능

`RAGEval_v1.12.md`: RAG(eval)评估기능

`RAGEval_v1.13.md`: RAG(eval)评估기능

`RAGEval_v1.14.md`: RAG(eval)评估기능

`RAGEval_v1.15.md`: RAG(eval)评估기능

`RAGEval_v1.16.md`: RAG(eval)评估기능

`RAGEval_v1.17.md`: RAG(eval)评估기능

`RAGEval_v1.18.md`: RAG(eval)评估기능

`RAGEval_v1.19.md`: RAG(eval)评估기능

`RAGEval_v1.20.md`: RAG(eval)评估기능

`RAGEval_v1.21.md`: RAG(eval)评估기능

`RAGEval_v1.22.md`: RAG(eval)评估기능

`RAGEval_v1.23.md`: RAG(eval)评估기능

`RAGEval_v1.24.md`: RAG(eval)评估기능

`RAGEval_v1.25.md`: RAG(eval)评估기능

`RAGEval_v1.26.md`: RAG(eval)评估기능

`RAGEval_v1.27.md`: RAG(eval)评估기능

`RAGEval_v1.28.md`: RAG(eval)评估기능

`RAGEval_v1.29.md`: RAG(eval)评估기능

`RAGEval_v1.30.md`: RAG(eval)评估기능

`RAGEval_v1.31.md`: RAG(eval)评估기능

`RAGEval_v1.32.md`: RAG(eval)评估기능

`RAGEval_v1.33.md`: RAG(eval)评估기능

`RAG
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
