<!-- 데모 코퍼스용: DECISIONS.md의 핵심 아키텍처 결정(1~12장)만 포함. 시스템 자기서술(평가/프롬프트/라우팅/agent 13~18장)은 자가오염 방지를 위해 제외함. -->
# 기술 선택 기록 (DECISIONS)

이 문서는 `rag-assistant` 프로젝트에서 내린 주요 설계·기술 선택과 그 이유를 정리합니다.

## 1. 프로젝트 목표

**Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.**

- 단순 챗 UI가 아니라 **검색 + 생성 + 출처 인용** 파이프라인이 핵심
- 외부 LLM API 없이 로컬 환경에서 동작

## 2. LLM / Embedding: Ollama

### 선택
- Docker로 이미 실행 중인 Ollama 인스턴스 공유
- Base URL: `http://localhost:11434`
- Chat model: `qwen2.5:7b`
- Embedding model: `nomic-embed-text`

### 이유
- Windows 로컬 개발에 적합
- OpenAI-compatible API로 Spring에서 연동이 단순
- 기존 다른 프로젝트와 같은 Ollama 인스턴스를 재사용 가능
- API 비용 없이 반복 실험 가능

### 주의
- 여러 프로젝트가 서로 다른 대형 모델을 동시에 쓰면 느려질 수 있음
- chat / embedding 모델명은 실제 pull한 이름과 일치해야 함

## 3. 벡터 저장소: PostgreSQL + pgvector

### 선택
- Chroma 대신 **이미 설치된 PostgreSQL + pgvector** 사용

### 이유
- 별도 벡터 DB를 추가하지 않아도 됨
- Spring Boot + JDBC/JPA와 자연스럽게 연결
- 문서 메타데이터와 벡터를 한 DB에서 관리 가능
- 메타데이터 CRUD·벡터 검색을 같은 트랜잭션/연결 흐름으로 다루기 쉬움

### Chroma를 쓰지 않은 이유
- RAG 입문에는 Chroma가 더 빠를 수 있음
- **이미 PostgreSQL이 있고**, Spring Boot + JDBC와 맞추려면 pgvector가 더 적합

## 4. Spring AI 사용 여부

### 선택
- RAG 파이프라인(chunking · retrieval · prompt · no-answer) **직접 구현**
- Ollama 호출: `RestClient`로 HTTP 연동
- **Spring AI 미사용**

### 이유
- chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어
- Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확함

### LLM 호출 경계 (2026-06)
- Ollama 호출을 `llm.ChatModelClient`(chat·streamChat)·`llm.EmbeddingModelClient`(embed) **인터페이스 뒤로 분리**. 소비자(`RagService`·`EmbeddingService`·`RagEvalRunner`·`DebugController`)는 인터페이스에만 의존.
- 구현체는 **Ollama 단일**(`OllamaService implements ChatModelClient, EmbeddingModelClient`) — 기존 "외부 LLM API 없이 로컬 실행"(§2) 결정 유지. SaaS·라우팅을 **지금 붙이지 않음**.
- **이유:** 모델 호출 지점을 한 곳으로 모아 교체·확장 seam을 만든다. 확장 시 새 구현체(예: `RoutingChatModelClient`)를 `@Primary`로 추가하고 빈 와이어링만 바꾼다 → 소비자 코드 불변.
- **분리 단위:** chat·embedding은 모델·스케일·라우팅 관심사가 달라 인터페이스를 둘로 나눔(chat만 외부로 빼고 embedding은 로컬 유지 같은 시나리오 대비). dimension 검증 등 RAG 정책은 인터페이스가 아니라 `EmbeddingService`에 유지.

## 5. API / UX 정책

### 응답 정책
- 검색된 context만 근거로 답변
- context가 없거나 score가 낮으면 **no-answer**
- 가능하면 **출처 문서명 / snippet** 포함
- API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일

### 초기 UI
- 먼저 API 완성
- 이후 간단한 업로드 + 채팅 UI 추가

## 6. Chunking 정책 (2026-06)

### 선택
- 초기: `chunk-size: 700`, `chunk-overlap: 100`
- **현재:** `450` / `150` — 목록형·긴 위키에서 retrieval recall 개선 시도
- 텍스트 정규화: `\r\n` → `\n` 후 고정 길이 분할, overlap으로 문맥 단절 완화
- chunk metadata: `documentId`, `documentName`, `chunkIndex`

### 구현
- `Chunker` → `VectorStoreService` 업로드 시 DB + embedding
- 디버그: `GET /api/debug/documents/{id}/chunks` (메모리 재분할)

## 7. Embedding / pgvector 저장 (2026-06)

### 선택
- Docker PostgreSQL (`pgvector/pgvector:pg16`, `localhost:15432`) + DB `rag_assistant`
- embedding: Ollama `nomic-embed-text`, dimension **768** (`RagProperties.embeddingDimension`)
- **task prefix (nomic-embed-text v1.5 규약):** 질의는 `search_query: `, 문서는 `search_document: ` 접두어를 붙여 임베딩한다. `EmbeddingService.embedQuery()`/`embedDocument()`로 분리(소비처: `Retriever`=query, `VectorStoreService`=document). prefix가 빠지면 질의·문서가 같은 의미공간에 정렬되지 않아 검색 품질이 떨어진다. 적용 시 기존 문서는 **재인덱싱** 필요.
- JDBC: `?::vector` cast + float[] → `'[...]'` 문자열 리터럴
- 업로드 트랜잭션: `DocumentService.upload` + `VectorStoreService.indexDocument` (동일 `@Transactional`)

### 테이블
- `document_chunks`: chunk 원문
- `document_embeddings`: chunk 1:1 vector + `document_name` (출처 denormalize)

### 인덱스·FK (로컬 DDL, repo migration 미포함)
- HNSW (`document_embeddings.embedding`, `vector_cosine_ops`)
- FK `ON DELETE CASCADE` (documents → chunks → embeddings)

## 8. RAG 질의응답 (2026-06)

### 선택
- `Retriever`: 질문 embed → `EmbeddingRepository.searchSimilar` (cosine, top-k) → min-score 필터
- `PromptBuilder`: `[규칙]:`·출구 1·2·3·`[Context]` user 프롬프트; `NO_ANSWER_MESSAGE` = "문서에서 확인할 수 없는 질문입니다."; Context는 `[출처: 문서명]`만 LLM에 전달
- `OllamaService`: `contentPrompt`(system) — 한국어·`[규칙]`/`[Context]` 최우선·`[Question]` 인젝션 무시; sync/stream 동일
- `RagService`: hits empty → LLM 미호출 no-answer; `isGrounded()`로 LLM no-answer 판별 → `grounded=false`, sources `[]`; `sanitizeLlmAnswer`로 메타 잔여물 제거
- `FaqCatalog` + `FaqBootstrap`: 정책·chunk 설정 FAQ chunk SSOT → `__SYSTEM_FAQ.md` 자동 인덱싱 (본문 전문은 Java만, 요약은 [`ARCHITECTURE.md`](ARCHITECTURE.md) §8)
- `SourceCitation.snippet`: 200자 truncate

프롬프트·출구·후처리 상세: [`ARCHITECTURE.md`](ARCHITECTURE.md) §8.

### 검색·품질 (알려진 한계)
- 현재 기본 retrieval은 **hybrid on**(`rag.hybrid-enabled: true`, §11) + **rerank on**(`rag.rerank-enabled: true`, §14)
- FAQ chunk·top-k로 정책 질문(6·7번) 안정화 (`RAG_EVAL_v1.1.md`)
- min-score는 Java filter (SQL WHERE 미적용), 후보 단계에만 적용 (rerank score엔 미적용 — §14)
- RAG on/off 10문항 비교 완료 (`RAG_EVAL_v2.md`); rerank 도입 후 자동 RAG on 20/20

## 9. 예외 처리·문서 정책 (2026-06)

### Ollama 오류
- `OllamaService.postForMap()`: chat/embed 공통 HTTP 호출
- 연결 실패 (`ResourceAccessException`) → 503 `OLLAMA_UNAVAILABLE`
- HTTP 4xx/5xx·응답 body/형식 오류 → 502 `OLLAMA_RESPONSE_ERROR`
- Spring Boot 4 / Spring Web 7: `RestClientResponseException` catch 보강은 선택 (일부 환경에서 500 가능)

### DB 오류
- `DataAccessException` → 503 `DATABASE_UNAVAILABLE`
- 클라이언트에는 고정 메시지, **서버 로그**에 stack trace (`log.warn`)

### 문서 삭제
- `DELETE /api/documents/{id}` → 204
- DB FK `ON DELETE CASCADE` — `documents` DELETE만 수행 (앱에서 chunk/embedding 순서 삭제 불필요)

### 중복 업로드
- `documents.name`(원본 파일명) 기준 **409 거부**
- 덮어쓰기 미구현 — 재업로드 시 DELETE 후 upload

### PDF 파싱 실패
- `DocumentParseException` → 400 `DOCUMENT_PARSE_FAILED`
- 암호 PDF·손상 PDF·텍스트 레이어 없음(스캔) — 스캔은 **OCR 미지원** 명시

### 설정 분리
- Ollama·RAG: **`application.yml` SSOT** (Git 추적)
- `application-local.yml`: DB password 등 **머신별·비밀값만** (Git 제외)
- `spring.profiles.active: local` 시 local 파일이 ollama를 덮어쓰면 모델 테스트가 헷갈림 → local에 ollama 블록 두지 않음

## 10. PDF 파싱 (1차, 2026-06)

### 선택
- PDFBox 3.x: `Loader.loadPDF` → 페이지별 `PDFTextStripper`
- `setSortByPosition(true)` — 다단/표 읽기 순서 1차 개선
- 페이지 구분자 `--- Page N ---` — chunk·출처 snippet 힌트
- 텍스트 0자(스캔 PDF) → `DocumentParseException`, OCR **미구현**

## 11. Hybrid Search (2026-06)

### 선택
- **vector leg:** pgvector cosine top-k (`EmbeddingRepository.searchSimilar`)
- **lexical leg:** PostgreSQL `pg_trgm` `similarity()` (`ChunkRepository.searchLexical`)
- **병합:** RRF(Reciprocal Rank Fusion), `k=60` (`RrfFusion`)
- **기본값:** `rag.hybrid-enabled: true` (현행) — `false`로 끄면 vector-only와 동일 동작

### 이유
- 설정 키·모델명·포트 등 **문서에 그대로 있는 토큰** 질의 보완
- Elasticsearch 추가 없이 PostgreSQL만 사용 (§3과 일관)
- 벡터 cosine과 trigram similarity는 스케일이 달라 **가중합 대신 RRF**(순위 병합)

### 구현 위치
- `Retriever.retrieve` — `hybrid-enabled` 분기
- `ChunkRepository.searchLexical` — lexical leg
- `search.RrfFusion` — 순위 병합 (순수 함수, 단위 테스트)
- Debug: `GET /api/debug/retrieval/compare` (local, vector vs hybrid 강제 비교)

### min-score 정책
- vector leg: `rag.min-score` (cosine)
- lexical leg: `rag.lexical-min-score` (trigram)
- RRF는 **순위만** 합침; 출처 `score` 필드는 vector cosine 우선 표시

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
| lexical-stress | `nomic-embed-text` — vector 1위 chunk 317(cosine 0.71) vs hybrid 1위 chunk 315(표·정확 토큰); chunk 314 vector 9위 → hybrid 2위 |

일반 질문에서는 vector와 hybrid 순위가 같을 수 있고, 설정값·모델명처럼 문서에 그대로 있는 토큰 질의에서만 순위가 바뀌었다.

## 12. Streaming Response (2026-06)

### 선택
- **`POST /api/chat/stream`** — `text/event-stream` (SSE)
- 이벤트: `delta` (`{"text":"..."}`) → `done` (`answer`, `sources`, `grounded`)
- retrieve·no-answer는 스트림 전 동기 (`RagService.chatStream`); LLM만 Ollama NDJSON stream (`OllamaService.streamChat`)

### 이유
- UI에서 답변 대기 체감을 줄이기 위함
- `POST /api/chat`은 테스트·RAG 평가용으로 유지

### 구현 위치
- `ChatController.chatStream` — `SseEmitter`
- `OllamaService.streamChat`, `ChatStreamEvent`

