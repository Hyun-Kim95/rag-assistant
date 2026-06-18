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
- **기본값:** `rag.hybrid-enabled: false` — vector-only와 동일 동작

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
- eval: rerank off 16/20 → rerank on **20/20**(5·6 회복 + 4 회복 + no-answer 유지). 설계·계획 상세는 [`PLAN_RERANKER_TOPK.md`](PLAN_RERANKER_TOPK.md).
- 1차 검색 후보(candidate-top-k=30) 밖으로 정답이 밀리면 rerank로도 못 살림 → candidate 폭 튜닝 또는 메타 코퍼스 정리 별도 검토.
