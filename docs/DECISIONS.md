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
- FAQ chunk·top-k로 정책 질문(6·7번) 안정화 ([`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md))
- min-score는 Java filter (SQL WHERE 미적용), 후보 단계에만 적용 (rerank score엔 미적용 — §14)
- RAG on/off 10문항 비교 완료 ([`RAG_EVAL_v2.md`](RAG_EVAL_v2.md)); rerank 도입 후 자동 RAG on 20/20

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

## 13. RAG 평가 자동화 (2026-06)

### 선택
- 고정 질문 세트 `eval/questions.json`(루트, SSOT) + `RagEvalRunner`로 동일 10문항을 자동 실행
- 결과: `score`·`grounded`·`sources`·`noAnswer`를 `eval/reports/`에 JSON(기계)·Markdown(사람)으로 저장
- 실행 트리거는 **CLI 플래그**(`--rag.eval.enabled`), `application.yml`에 평가 키를 두지 않음
- 절차·산출물 구조: [`ARCHITECTURE.md`](ARCHITECTURE.md) §11

### 룰 기반 채점 (LLM-as-judge 미사용)
- `EvalScorer`가 키워드(`mustContainAll/Any`)·금지어(`mustNotContain`)·`grounded`·`sources`·no-answer 문구로 0/1/2점 결정
- **이유:** LLM judge는 비용·비결정성·편향이 있고, 본 코퍼스는 정답이 문서에 있는 closed-book QA라 결정적 룰로 회귀·추세를 잡기 충분
- **한계:** 표현 변형에 약함 → 정밀 채점은 사람 리뷰로 보완 (현재는 1차 게이트 용도)

### REST 대신 서비스 직접 호출
- `RagEvalRunner`가 `RagService.chat`(on)·`OllamaService.chat`(off)를 직접 호출
- **이유:** 평가 대상은 RAG 파이프라인 품질이지 REST 레이어가 아님 → 직렬화·포트·타임아웃 변수를 제거해 재현성 확보
- `RAG_OFF`는 검색·`PromptBuilder` 없이 LLM만 (v2 대조군과 동일 의미)

### 산출물 버전 관리 정책
- 출력 루트는 `eval/reports/`(= `questions.json`과 같은 `eval/` 트리) — `build/`가 아니라 **Git 추적 경로**
- `latest-{mode}`·`compare-latest.md`는 **커밋** (포폴·재현 증거)
- `runs/{timestamp}_{MODE}`는 **gitignore** — 프롬프트 튜닝마다 쌓이는 이력은 로컬 전후 비교용
- 모드별로 파일을 분리해 RAG on/off가 서로 덮어쓰지 않게 함

### 알려진 회귀 (해소됨 → §14)
- (이력) reranker 도입 전 자동 RAG on **18/20** — 4번(Chroma)·또는 5·6번(정책)이 코퍼스 자가오염으로 0점이던 시기가 있었음.
- **현재 §14 reranker + 프롬프트 보완으로 RAG on 20/20** 회복. retrieval 순위 문제(5·6)는 rerank로, "근거 함의 미활용"(4번)은 프롬프트 예외 규칙으로 해결.

## 14. Reranker + 2단계 retrieval (2026-06)

### 배경 / 문제
- 정책·개념형 질문(5·6번)이 반복 0점. 디버그 결과 retrieval은 성공하나, RAG 동작을 설명하는 **메타 문서 chunk**가 top-k를 채우고 실제 정답 chunk가 밀려나는 **코퍼스 자가오염**.
- 원인: 기존 `top-k`(=10) 하나가 **검색 후보 수**와 **프롬프트 context 수**를 겸함.

### 선택
- retrieval을 2단계화: `retrieve(넓은 후보) → rerank → 상위 top-n만 context`.
- reranker: **전용 cross-encoder `BAAI/bge-reranker-v2-m3`** (HF TEI 컨테이너, GPU/CPU). Spring이 HTTP `POST /rerank` 호출.
- 기본 on (`rag.rerank-enabled: true`). 후보 폭 `candidate-top-k=30`(vector·lexical 두 leg 공통), context `rerank-top-n=8`.

### 이유
- 1차 검색(bi-encoder, 임베딩 cosine)은 질문·문서를 따로 벡터화해 빠르지만 거칠다 → 메타 chunk를 못 거름.
- 2차 rerank(cross-encoder)는 `[질문+chunk]`를 함께 입력해 관련도를 직접 판정 → 정밀. 소수 후보(30)에만 적용해 비용 감당.
- 전용 모델을 별도 컨테이너로 둬 Ollama·DB와 분리(횡단 부하 격리), GPU 활용.

### 파이프라인 / 구현 위치
- `Retriever.retrieve`: `rerank-enabled` 분기. on이면 candidate-top-k로 넓게 뽑고(`retrieveCandidates`) → `Reranker.rerank` → top-n. off면 기존 top-k 경로.
- `search.Reranker`: TEI `/rerank`(`{query, texts}` → `[{index, score}]`) 호출, 응답 `index`로 원본 후보 재매핑하며 `SearchHit` score를 rerank score로 교체.
- `config.RerankerProperties`(`rag.reranker.*`) + `AppConfig.rerankerRestClient`(connect/read **timeout** 필수).
- **min-score 위치:** cosine/lexical min-score는 **후보 단계에만** 적용. rerank score는 스케일이 달라(음수 가능) 별도 임계 없이 상위 top-n만 사용.

### fallback (회귀 가드)
- TEI 호출 실패(연결 불가·타임아웃·파싱 오류) 시 `Reranker`가 예외를 삼키고 **원본 검색 순서 + top-n 컷**으로 반환 → RAG 중단 없음.
- 검증: TEI 다운 상태 RAG on **13/20**(품질만 저하, no-answer 군 유지, 앱 정상 완주).

### 프롬프트 보완 (4번 회복)
- rerank가 정답 chunk를 1순위로 올려도, "pgvector 대신 Chroma를 안 쓴 이유" 질문에서 LLM이 근거(= pgvector 선택 이유)를 "Chroma 미사용 이유"로 매핑하지 않아 0점.
- `PromptBuilder` 규칙에 예외 추가: **[Context]가 'A 대신 B 선택/사용 + 이유'를 명시하면 'A를 쓰지 않은 이유'로 답해도 됨**(근거 기반이므로 추측 아님). no-answer 군은 Context가 비어 이 규칙에 도달하지 않아 영향 없음.

### 영향 / 한계
- `SourceCitation.score` 의미가 cosine→**rerank score**로 바뀜(응답 필드 스케일 변화). debug `/api/debug/retrieval/compare`는 의도적으로 **rerank 전** 후보를 보여줌(진단용).
- eval: rerank off 16/20 → rerank on **20/20**(5·6 회복 + 4 회복 + no-answer 유지).
- 1차 검색 후보(candidate-top-k=30) 밖으로 정답이 밀리면 rerank로도 못 살림 → candidate 폭 튜닝 또는 메타 코퍼스 정리 별도 검토.

## 15. Model Router (다중 LLM 라우팅 + 폴백, 2026-06)

### 배경 / 목표
- chat 추론을 **2개 이상 provider로 라우팅하고 실패 시 폴백**하며, 그 결과를 헬스·관측·평가에 연결해 "운영 가능한 Model Router"로 만든다.
- §4(LLM 호출 경계)에서 만들어 둔 `llm.ChatModelClient` seam 위에 **소비자(`RagService`) 코드 변경 없이** 라우터·2nd provider만 얹는다.

### 선택
- **2nd provider = OpenAI 호환 클라이언트 1개**(`OpenAiCompatChatClient`, `/v1/chat/completions`). base-url 교체만으로 SaaS(Groq·OpenAI·Gemini 호환 등)와 self-hosted(vLLM 등)를 모두 커버 → "SaaS & self-hosted 동시 연동"을 한 구현으로 충족.
- **라우터 = `RoutingChatModelClient`(`@Primary`)**: `[요청 provider?] + default-provider + fallback-order` 1회 순회(중복 제거). 동기 `chat`만 폴백, **`streamChat`은 폴백 없이 default leg 1개**(토큰 스트림 중 끊기면 폴백이 지저분해지므로 1차 제외).
- **라우팅 정책 1차 = 단순 명시 규칙**(설정 체인 + 요청 provider 우선). 질문 길이·난이도 기반 분기는 `chain()` 확장 지점만 남기고 후속.
- **공통 예외 타입 도입**: `LlmUnavailableException`(503)·`LlmResponseException`(502)을 상위 타입으로 두고 `Ollama*Exception`이 이를 상속 → 라우터는 상위 타입만 catch해 폴백, 기존 `GlobalExceptionHandler` 매핑은 그대로 유지. 전체 실패는 `AllProvidersUnavailableException`(503 `LLM_ALL_PROVIDERS_UNAVAILABLE`).
- **무료 전략(비용 0)**: Ollama(`qwen2.5:7b`)를 primary, **Groq 무료 티어(`llama-3.1-8b-instant`)**를 fallback. vLLM은 GPU 필요라 "무료"가 아니어서 제외.

### 무료/비밀값 운영
- `application.yml`은 `llm.openai-compat.enabled: false` 기본(키 없이도 기동, groq는 `available()=false`로 스킵 → Ollama 단독 = 기존 동작).
- 키는 `application-local.yml`(gitignore)에서 `enabled: true` + `api-key`로만. `application.yml`(커밋 대상)에 키 금지.
- 가용성 점검은 **config 수준**(`available()` = enabled + api-key 존재). 실제 도달성 핑은 하지 않음 — 비용·레이트리밋 0, 실장애는 폴백이 흡수.

### 헬스 status 규칙 변경
- 기존 `core = ollama·db`에서 **`database`만 core**로. "chat provider ≥1 UP"이 아니면 `DOWN`.
- `/api/health` `dependencies`는 provider 이름별 상태(`ollama-7b`, `groq`, …) + `database` + `reranker`. Ollama leg는 `/api/tags` 실제 핑(UP/DOWN), SaaS leg는 config 수준(UP/DISABLED).

### 관측 / 평가
- `QueryTelemetry`에 `provider`·`fallbackUsed` 추가 → 요청 로그 한 줄에 어떤 leg가 응답했는지·폴백 여부 기록. `ChatResponse.provider`로 API 응답에도 노출(검색 hit 0건이면 null).
- `RagEvalRunner`에 `--rag.eval.providers=ollama-7b,groq` → leg별 RAG_ON 실행, `latest-rag-on-{provider}.{json,md}` + `compare-providers.md`(provider별 total·avgLatencyMs·grounded).

### 결과 (2026-06, 자동 측정)
| provider | total | avgLatencyMs | grounded |
| --- | ---: | ---: | ---: |
| ollama-7b | 19/20 | 11,424 | 7/10 |
| groq | 19/20 | 4,301 | 7/10 |

- 동일 품질(19/20)에 **Groq가 약 2.6배 빠름** → self-hosted vs SaaS leg의 지연·품질 트레이드오프를 숫자로 제시.
- (6번 1/2는 라우터 무관 — 룰 기반 채점기의 키워드 커버리지 한계, §13.)

### 구현 위치
- `llm/ChatModelClient`(`name()`/`available()`/`chat(prompt,provider)` default), `llm/OpenAiCompatChatClient`, `llm/RoutingChatModelClient`(`@Primary`), `llm/ChatPrompts`(공유 system 프롬프트).
- `config/RoutingProperties`·`OpenAiCompatProperties` + `AppConfig`(`openAiCompatRestClient` timeout 빈).
- `exception/Llm*Exception`·`AllProvidersUnavailableException`·`UnknownProviderException` + `GlobalExceptionHandler`.
- `service/HealthService`(provider 상태·status 규칙), `observability/QueryTelemetry*`, `eval/RagEvalRunner`·`EvalReport`·`EvalReportWriter`.

### 한계
- **임베딩은 라우팅 밖**(Ollama `nomic-embed-text`, 차원 768 고정). Ollama가 없으면 retrieve(질문 임베딩)부터 실패 → chat 폴백만으론 RAG 전체를 구제하지 못함.
- 로컬 sLM leg(작은 모델) 동시 사용 → **§16에서 구현**(`OllamaChatClient` 모델 파라미터화 + `ollama-1b` 빈 분리).
- 요청 provider 지정은 **동기 `/api/chat`만**. 스트리밍은 default 라우팅.
- SaaS 헬스는 config 수준(실제 도달성 아님).

## 16. Model Router 심화 — 난이도 기반 라우팅 + 로컬 sLM leg (2026-06)

### 배경
- §15는 **fixed 라우팅**(요청 provider + 설정 체인 + 폴백)까지였다. 심화 목표는 **질문 난이도로 동적 분기**해 쉬운 질문은 작은 모델(sLM)로 싸게·빠르게, 어려운 질문만 큰 모델로 보내는 전략이 **이 코퍼스·하드웨어에서 실제로 이득인지**를 숫자로 검증하는 것.
- §15 한계의 "로컬 sLM leg 미구현"을 해소한다.

### 선택
- **sLM leg = `ollama-1b`(`llama3.2:1b`)** 추가. 답변 모델 2단(easy→1b, hard→`ollama-7b`). 이를 위해 `OllamaService`의 chat을 `OllamaChatClient`로 **추출·모델 파라미터화**하고 7b·1b 두 빈으로 분리(`OllamaService`는 임베딩 전용).
- **분류기 = 별도 모델 `qwen2.5:3b`**(답변 모델과 분리). 처음엔 1b로 분류했으나 짧은 사실 질문(예: "Ollama base-url은?")도 HARD로 **오분류·불안정** → 3b로 교체하니 안정. 분류 **실패 시 HARD 폴백**(품질 우선, 안전한 기본값).
- **분류 입력은 증강 프롬프트가 아니라 원문 질문**. 초기엔 RAG context가 붙은 프롬프트를 분류기에 넣어 길이·복잡도 때문에 전부 HARD로 분류되는 버그가 있었다 → `ChatModelClient.chat(prompt, provider, routingText)` 오버로드로 **분류용 텍스트(routingText=원문 질문)** 를 프롬프트와 분리.
- **`routing-strategy` 토글**(`fixed`|`difficulty`, 기본 `fixed`) + `llm.difficulty.easy-provider`/`hard-provider` 매핑. 요청 `provider` 지정은 항상 최우선(전략 무시).
- **관측:** `QueryTelemetry`에 `difficulty`(EASY/HARD/-) 추가 → 요청 로그 한 줄에 분류 결과 기록.
- **Ollama 호출 안정화:** `num_predict=1024`(무한 생성 방지), `num_ctx=4096`(1b가 기본 32K 컨텍스트로 로드되며 8GB GPU에서 스톨 → 4096 고정; RAG 프롬프트엔 충분), `ollamaRestClient` read-timeout 120s(서버 스톨 시 무한 대기 방지).

### 결과 (2026-06, `num_ctx=4096`, 단일 8GB GPU / `eval/reports/compare-providers.md`)
| provider | total | avgLatencyMs | grounded | 비고 |
| --- | ---: | ---: | ---: | --- |
| ollama-1b | 10/20 | 6,098 | 10/10 | 작은 모델 전량 (빠름, 품질↓·no-answer 미준수) |
| ollama-7b | 19/20 | 57,631 | 7/10 | 큰 모델 전량 (느림, 품질↑) |
| router(difficulty) | 17/20 | 64,923 | 8/10 | 난이도 분기 (EASY 4문항→1b, HARD 6문항→7b) |

- **분류는 정확히 동작:** 사실 단답형 4문항은 EASY→1b, 비교·요약·no-answer 6문항은 HARD→7b로 분기(telemetry `difficulty` 확인). 품질은 1b(10) < **router(17)** < 7b(19)로, 어려운 질문을 7b로 보내 7b에 근접한 점수를 **싼 경로를 섞고도** 유지.
- **그러나 지연 이득은 없음:** router(64.9s) ≈ 7b(57.6s)로 오히려 약간 높다. 원인은 생성 속도가 아니라 **VRAM 경합**이다 — 단일 8GB GPU에 분류기(3b)+답변모델(1b/7b)이 동시 상주하지 못해, 모델 교체 시 활성 모델이 CPU로 일부 오프로드되며 7b 생성이 ~11s(§15, 7b 단독 상주)에서 ~55s로 느려진다. EASY→1b 문항도 직전 분류기·7b 잔류 때문에 1b 단독(6s)이 아니라 50s대가 나온 경우가 많다.
- **결론(정직):** 본 코퍼스·하드웨어에서 난이도 라우팅은 **품질을 7b 근처로 유지**하지만 **지연·비용 이득은 없다**. 이득은 (a)모든 leg가 상주 가능한 충분한 VRAM, (b)룰 기반·상주형 경량 분류기, (c)원격/별도 분류 엔드포인트가 있을 때 실현된다. 따라서 **기본 전략은 `fixed` 유지**, `difficulty`는 옵트인(연구·환경별 튜닝용)으로 둔다.

### 구현 위치
- `llm/OllamaChatClient`(모델 파라미터화·`num_predict`/`num_ctx`), `service/OllamaService`(임베딩 전용화), `llm/DifficultyTier`·`llm/DifficultyClassifier`(3b 분류, 실패→HARD), `llm/RoutingChatModelClient`(`pickByStrategy`), `llm/ChatModelClient`(`chat(prompt,provider,routingText)`).
- `config/RoutingProperties`(`routingStrategy`·`Difficulty`), `config/OllamaProperties`(`smallChatModel`·`classifierModel`), `config/AppConfig`(`ollama7bChatClient`·`ollama1bChatClient` 빈, `ollamaRestClient` timeout), `resources/application.yml`(`routing-strategy`·`difficulty`·`small-chat-model`·`classifier-model`).
- `observability/QueryTelemetry*`(`difficulty`), `eval/RagEvalRunner`(`router` 토큰 = 설정 전략으로 라우팅).

### 한계
- **지연 이득은 하드웨어 의존**(위 결론). 단일 소형 GPU에서는 다중 모델 경합이 지배적.
- **분류기 호출이 매 질문 1회 추가 비용**(3b 생성). 분류 결과 캐싱·룰 기반 사전 분기 미적용.
- 분류는 **2단(EASY/HARD)**·**모델 2개**만. 다단·N개 모델 일반화 미적용.
- `difficulty` 전략은 **동기 `/api/chat`만**. 스트리밍은 fixed default 라우팅.

## 17. Tool Calling Agent (2026-06)

### 배경 / 목표
- 기존 `/api/chat`은 단발 RAG(검색 1회 → 생성)다. LLM이 **필요할 때 도구를 스스로 호출**해 멀티스텝으로 답을 구성하는 에이전트 경로(`POST /api/agent`)를 추가한다.
- 기존 RAG·`/api/chat`·스트리밍은 건드리지 않는다(회귀 0 목표).

### 선택
- **transport 분리**: tool calling 전용 `AgentChatClient` 인터페이스를 신설한다. 기존 `ChatModelClient`(텍스트 in/out)와 분리해 회귀 0. 구현은 `OpenAiCompatAgentClient`(Groq 등 OpenAI 호환 `tools`/`tool_calls`), `OllamaAgentClient`(Ollama tool 포맷 차이 흡수) + `RoutingAgentChatClient`(`@Primary`, groq→ollama 폴백).
- **provider 전략**: Groq leg 우선 + Ollama 폴백(tool calling 안정성↑, Groq 키 미설정 시 Ollama 단독).
- **도구 = 2종**: `search_documents`(기존 `Retriever.retrieve()` 래핑 — hybrid+rerank 그대로 재사용), `list_documents`(문서 목록). `@Component` `AgentTool`을 `ToolRegistry`가 자동 수집·디스패치(확장 포인트).
- **루프 안전장치**: `agent.max-steps`(기본 5)·`agent.timeout-ms`. tool 오류·잘못된 인자·없는 도구명은 텍스트로 모델에 되먹여 복구 기회를 준다(스텝 1 소모). `stopReason` = `FINAL`|`MAX_STEPS`|`TIMEOUT`|`NO_PROVIDER`|`ERROR`.
- **grounded/no-answer**: `/api/chat`과 동일 철학. 비정상 종료(TIMEOUT·MAX_STEPS)나 모델이 '근거 없음'으로 답하면 `sources`를 비우고 `grounded=false`.
- 동기 MVP. SSE 스트리밍은 후속.

### 구현 위치
- `llm/agent/`(`AgentChatClient`·`OpenAiCompatAgentClient`·`OllamaAgentClient`·`RoutingAgentChatClient` + 전송 모델 `AgentMessage`/`ToolCall`/`ToolSpec`/`AgentTurn`), `agent/AgentOrchestrator`, `agent/tool/`(`AgentTool`·`ToolResult`·`ToolRegistry`·`SearchDocumentsTool`·`ListDocumentsTool`).
- `controller/AgentController`(`POST /api/agent`), `dto/AgentRequest`·`AgentResponse`·`AgentStep`, `config/AgentProperties`(`agent.max-steps`·`timeout-ms`·`provider-order`), `config/AppConfig`(agent 빈 와이어링), `resources/application.yml`(`agent.*`).

### 한계
- 도구는 2종(검색·목록)뿐. 계산·외부 조회 등 확장 미적용. → **§18에서 본문 도구(`read_document`·`summarize_document`) 추가.**
- 폴백은 루프 시작 시 provider 선택 수준(턴 중간 leg 교체 아님).
- SSE 스트리밍·멀티턴·프론트 UI는 후속. → **§18에서 멀티턴 메모리·스트리밍 스텝 UI 추가.**
- 소형 모델 tool calling 신뢰도는 코퍼스·프롬프트에 민감(자가오염 코퍼스에서 품질 저하).
- 에이전트 전용 평가 하네스는 후속.

## 18. Tool Calling Agent v2 — 본문 도구 · 멀티턴 메모리 · 스트리밍 (2026-06)

### 배경 / 목표
- v1(§17)은 `search_documents`·`list_documents` + 단발 멀티스텝 루프였다. "RAG 래퍼"를 넘어 **에이전트다움**을 강화한다: ① 단발 RAG가 못 하는 **본문 접근 도구**, ② **멀티턴 대화 메모리**, ③ **스트리밍 + 스텝 UI**.
- 기존 `/api/chat`·`/api/chat/stream`·v1 `/api/agent`는 건드리지 않는다(회귀 0).

### 선택
- **본문 도구 2종 추가**: `read_document`(특정 문서를 청크 범위로 실제 본문 반환 — `ChunkService`, top-k 단편이 아니라 연속 구간), `summarize_document`(문서 전체 본문 → LLM 요약). 둘 다 토큰 폭증 방지를 위해 상한(`read-max-chunks`·`read-max-chars`·`summarize-max-chars`)을 둔다. D9(요약 방식)는 1차 truncate(상위 본문 예산)로, map-reduce는 후속.
- **멀티턴 메모리 = 무상태(A안)**: 클라이언트가 이전 대화 `messages[]`(role·content)를 요청에 포함, 서버는 무상태. system 다음·현재 user 앞에 시드하고 `agent.max-history-turns`로 최근 N턴만(오래된 건 드롭). 이전 턴의 **도구 출력은 히스토리에 넣지 않음**(답변 텍스트만 맥락). 무인증·로컬·기존 무상태 RAG와 정합, 구현 단순(서버 세션·TTL 불필요).
- **스트리밍 SSE**: `POST /api/agent/stream`. 이벤트 `step`(tool_call/tool_result) → `delta`(최종 답) → `done`/`error`. 도구 호출은 생성 도중 실시간 방출(에이전트 체감의 핵심), **최종 답은 생성 완료 후 조각(delta)으로 흘림**(tool-calling 응답의 진짜 토큰 스트리밍은 까다로워 MVP에서 보류). 폴백 없는 default leg 단독(`/api/chat/stream`과 동일 정책). `SseEmitter` + 전용 executor, payload는 JSON 문자열로 직렬화해 전송(컨버터 모호성 회피).

### 런어웨이 방지 (실사용에서 발견·수정)
- **문제:** 소형 모델(Groq `llama-3.1-8b-instant`)이 목록 질문에도 `summarize`·`read_document`를 과호출하고, `read_document`의 "이어서 더 읽으려면…" 안내를 따라 `fromChunk`를 끝없이 증가시켜 **수백 회 호출 → 컨텍스트 초과 오류**. `max-steps`(모델 턴)는 한 턴에 도구를 수십 개 부르면 못 막았다.
- **수정:** ① `agent.max-tool-calls`(기본 10) **도구 호출 총량 상한** 추가, 초과 시 루프 중단. ② 상한/`max-steps` 소진 시 **도구 없이 1회 강제 종결**(`tools=[]`로 호출 → 모델이 수집 정보로 텍스트 답을 내고 종료). ③ 상한이 **턴 중간**에 걸릴 때 실행한 도구만 assistant 메시지에 남겨 `tool_calls`↔`tool` 응답 **개수 일치**(불일치 시 Groq가 400으로 거부 → 강제 종결이 빈 응답이 되던 버그). ④ `read_document`의 연속 호출 부추김 문구 제거.
- **모델 상향:** 8b는 한국어 도구 인자(검색 쿼리)를 깨뜨려(`"번드 버트 총창소"` 등) 검색이 빗나갔다. 에이전트 leg를 `llama-3.3-70b-versatile`로 올리니 쿼리 품질·자기교정이 개선(예: "DECISIONS.md에서 벡터 저장소?" → PostgreSQL + pgvector 정답). 단 이 모델 값은 `/api/chat`과 공유(OpenAiCompat) — 분리는 후속.

### grounded / 상태
- v1 철학 계승. 목록형 답(`list_documents`)은 본문 인용이 아니라 `grounded=false`(출처 없음). read/summarize는 문서 단위 `SourceCitation` 1건을 누적해 `grounded=true`.

### 구현 위치
- `agent/AgentStreamHandler`(이벤트 싱크, NOOP=동기), `agent/AgentOrchestrator`(`run`/`runStreaming` + `loop` 통합·`seedHistory`·`forceFinalAnswer`·도구 상한), `agent/tool/ReadDocumentTool`·`SummarizeDocumentTool`.
- `controller/AgentStreamController`(`POST /api/agent/stream`, `SseEmitter`), `dto/ConversationTurn`·`AgentRequest`(`messages` 확장), `config/AgentProperties`(`maxToolCalls`·`maxHistoryTurns`·`readMaxChunks`·`readMaxChars`·`summarizeMaxChars`), `config/AppConfig`(`agentStreamExecutor` 빈), `resources/application.yml`(`agent.*`), `static/`(에이전트 모드 토글·스텝 칩·멀티턴·SSE).

### 수용 기준 / 테스트
- AC-08~13(본문 도구·멀티턴·히스토리 상한·스트리밍 이벤트 순서·빈 입력 400)을 `AgentOrchestratorTest`·`AgentStreamControllerTest`로 자동화(Mockito, DB·LLM 비의존). AC↔테스트 `@DisplayName`로 1:1 추적.

### 한계
- `summarize_document`는 1차 truncate(상위 본문 예산) — 긴 문서 전체 정확 요약(map-reduce) 미적용.
- 스트리밍 최종 답은 생성 완료 후 조각 전송(진짜 토큰 스트리밍 아님).
- 에이전트 leg 모델이 `/api/chat`과 공유 — 에이전트 전용 모델 분리 미적용.
- 소형 모델 tool calling 규율은 여전히 모델 의존(상한·강제 종결로 답은 보장하나 과호출 자체는 모델 품질에 좌우).

## 19. 운영 지표 관측 (Metrics & Observability, 2026-06)

### 배경 / 목표
- 구조적 요청 로그(§12)는 휘발성 INFO 한 줄이라 **P95·토큰 비용 같은 분위수·합계**를 뽑기 어렵다. 채널별(chat·agent·voice) **품질·지연·비용·신뢰 지표를 집계 API로 조회**하는 최소 관측 레이어를 추가한다.
- 외부 메트릭 수집기/시계열 DB(Prometheus·Grafana·OTel)·실시간 알림·과금/청구는 범위 밖. 토큰 비용은 **사용자 과금이 아니라 운영 비용 추정(operational cost)** 이다.
- 기존 `/api/chat`·`/api/agent`·voice 경로는 건드리지 않는다(회귀 0, 지표 적재는 부가 1회).

### 선택
- **영속화 = 신규 `query_logs` 테이블**(대안: 로그 파싱 / 인메모리 윈도우). chat/agent 인터랙션 1건 = 1행. voice `call_*`와 동일하게 `schema.sql`(`CREATE TABLE IF NOT EXISTS`)로 자동 생성 → 재현·집계 용이, 운영 일관. **질문 원문·답변 본문은 저장하지 않는다**(지표·메타만 → 기존 텔레메트리 PII 회피 계승).
- **분위수 = PostgreSQL `percentile_cont`**(대안: 앱 메모리 계산). 네이티브·대량 OK, "평균 금지" 원칙에 맞춰 P95를 1차 기준(P50·P99 병기).
- **적재 지점 = `QueryTelemetryContext.endAndLog()`의 finally**. `QueryLogWriter`로 스냅샷 1행. **적재 실패는 요청을 막지 않는다**(통화 로그와 동일 철학, 예외 흡수). 신규 쓰기 엔드포인트 없음.
- **토큰·비용**: provider 응답에서 추출(Ollama `prompt_eval_count`/`eval_count`, OpenAI-호환 `usage`, 미제공 시 `null`). `estimated_cost = Σ(토큰 × metrics.cost.providers.<name> 단가)`. 로컬(Ollama)은 단가 0(=금전비용 0). 실단가: groq `llama-3.1-8b-instant` $0.05/1M in·$0.08/1M out.
- **집계 API `GET /api/metrics/summary`**: 기간·채널·provider 필터로 품질(grounded/no-answer)·지연(P50/P95/P99 + 단계 P95)·비용(토큰 avg/P95·provider 분해)·신뢰(voice handoff/완료율)·채널별 North Star(chat→groundedRate, voice→taskCompletionRate). voice는 `query_logs`가 아니라 `call_sessions`/`call_turns`에서 집계(중복 적재 없음).
- **추이 API `GET /api/metrics/timeseries`(드리프트)**: 기간을 `bucket`(day 기본·week·hour)으로 쪼개 버킷별 시계열. **eval 리포트(`runs/`)가 아니라 `query_logs` 시간축 집계**(`date_trunc(bucket, created_at)` GROUP BY)를 재사용한다. `bucket`은 식별자라 바인딩 불가 → 서버 화이트리스트(`hour|day|week`)로만 SQL 주입 차단.
- **상태/오류 계약**: 0건 구간은 200 + 지표 `null`(빈 상태, 오류 아님). `from>to`는 **400**(기존 `ErrorResponse` 재사용). `metrics.enabled=false`면 적재 안 하고 집계/추이 API는 **404**.
- **대시보드(선택, 채택)**: `static/metrics.html` + `js/metrics.js` 읽기 전용 1페이지. 의존성 없는 인라인 SVG 라인차트로 추이 표시, 기존 `app.css` 토큰 재사용. 로딩/빈/오류/비활성(404) 상태 처리.
- **RAGAS 연동 = 보류(안 함)**: faithfulness/answer-relevancy/context-precision(LLM-as-judge)은 **무료·로컬 PoC 전제상 비용 대비 효용이 낮아 미착수**. RAGAS는 Python 스택이라 스택 이원화 비용도 큼. 이미 `grounded` 플래그 + 룰 기반 eval(§13) + 드리프트 뷰로 기본 품질 모니터링은 충족. 착수 트리거: 유료 전환으로 judge 예산 확보 또는 품질 SLO 도입 시.

### 구현 위치
- `observability/`(`QueryLog`·`QueryLogWriter`, `QueryTelemetry`/`QueryTelemetryContext`에 토큰·채널·stopReason 확장), `repository/QueryLogRepository`(insert·summarize·timeseries)·`CallLogRepository`(`summarizeSessions`), `metrics/MetricsService`, `controller/MetricsController`(`/api/metrics/summary`·`/timeseries`), `dto/MetricsSummaryResponse`·`MetricsTimeseriesResponse`, `config/MetricsProperties`·`AppConfig`(텔레메트리 와이어링), `llm/`(`OllamaChatClient`·`OpenAiCompatChatClient`·`agent/OllamaAgentClient`·`agent/OpenAiCompatAgentClient`에 토큰 기록), `agent/AgentOrchestrator`(begin/recordChannel/endAndLog).
- `resources/schema.sql`(`query_logs` + 인덱스), `resources/application.yml`(`metrics.*`), `static/metrics.html`·`js/metrics.js`·`css/app.css`, `index.html`(대시보드 링크).

### 한계
- 온라인(운영) 환각률은 정답 라벨이 없어 직접 산출 불가 → `groundedRate`·`noAnswerRate`를 대리지표로, 정밀 환각률은 eval 세트(§13)에서 본다.
- voice 토큰·비용은 미수집(후속). voice는 `call_*` 기반 신뢰 지표만 집계.
- LLM-as-judge 정성 평가(RAGAS) 미적용(위 보류 결정).
- `/api/metrics/*`·`/metrics.html`은 로컬 비인증. 운영 노출 시 권한 가드는 별도 합의.
