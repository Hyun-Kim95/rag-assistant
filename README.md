# RAG Assistant

> Spring Boot 기반 RAG 검색·생성 엔진 (hybrid + rerank, 출처 인용)

Ollama와 PostgreSQL pgvector로 업로드 문서를 검색해 답하는 Spring Boot RAG 백엔드입니다. 검색·생성·출처 인용 파이프라인을 REST API로 제공합니다.  
문서를 업로드하면 chunk → embedding → 검색 → 생성 순으로 처리하고, 답변과 함께 출처(sources)·`grounded` 여부를 반환합니다.

**스택:** Java 17, Spring Boot, Gradle, Ollama (`qwen2.5:7b`, `nomic-embed-text`), PostgreSQL + pgvector 
검색: hybrid(`pg_trgm` + RRF, `rag.hybrid-enabled` 기본 `true`) + rerank(TEI cross-encoder `bge-reranker-v2-m3`, `rag.rerank-enabled` 기본 `true`, 미기동 시 fallback)

설계·선택 이유: [`docs/DECISIONS.md`](docs/DECISIONS.md) · API·DB·설정: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

## Demo

문서 업로드 후 RAG 질의·출처·no-answer 동작 (~45초 분량).

<img width="1216" height="742" alt="Image" src="https://github.com/user-attachments/assets/1e1830a6-3e13-4a6d-b255-7d9659e46fc5" />

## 실행

**필요:** JDK 17, Ollama (`http://localhost:11434`), PostgreSQL + pgvector, TEI reranker (`http://localhost:8085`, 기본 on·미기동 시 fallback)

> **의존성 등급:** Ollama·DB는 **필수**(없으면 동작 불가 → `/api/health` `DOWN`·503). TEI reranker는 **품질 의존성**으로, 미기동 시 fallback(원본 검색 순서)으로 **서비스는 계속 동작**하되 검색 품질만 떨어진다 → `/api/health`는 `DEGRADED`(200)로 표시.

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
# reranker 모델 (HF TEI 컨테이너가 최초 기동 시 자동 다운로드)
docker compose up -d tei-reranker   # BAAI/bge-reranker-v2-m3, http://localhost:8085
```

1. PostgreSQL 준비 — 예: `pgvector/pgvector:pg16`, `localhost:15432`, DB `rag_assistant`  
   extension·테이블·인덱스는 수동 DDL ([`ARCHITECTURE.md`](docs/ARCHITECTURE.md) §6, hybrid 시 [`DECISIONS.md`](docs/DECISIONS.md) §11)
2. (선택·기본 on) TEI reranker 기동 — `docker compose up -d tei-reranker`
   미기동 시 rerank를 건너뛰고 원본 검색 순서로 동작 (fallback)
3. `cp application-local.yml.example application-local.yml` 후 DB 비밀번호 입력
4. `gradlew.bat bootRun` (또는 `./gradlew bootRun`)
5. http://localhost:8080 — 문서 업로드 후 채팅

Ollama·RAG 설정은 `src/main/resources/application.yml`만 수정합니다.

## API (핵심)

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/documents/upload` | 문서 업로드 + 인덱싱 |
| `GET` | `/api/documents` | 문서 목록 |
| `DELETE` | `/api/documents/{id}` | 문서 삭제 |
| `POST` | `/api/chat` | RAG 응답 (JSON) |
| `POST` | `/api/chat/stream` | RAG 스트리밍 (SSE) |
| `GET` | `/api/health` | 앱 + 의존성 상태. core(ollama·db) DOWN → `DOWN`·503, reranker만 DOWN → `DEGRADED`·200 |

`local` 프로필: Swagger http://localhost:8080/swagger-ui.html, debug API (`/api/debug/...`)

## RAG 평가

고정 질문 세트(`eval/questions.json`)로 RAG 파이프라인 품질을 **자동 측정**합니다.
각 문항의 `score`·`grounded`·`sources`·`noAnswer`를 룰 기반으로 채점해 JSON/Markdown 리포트로 남깁니다.

```bash
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_OFF"
```

산출물 (`eval/reports/`):

- `latest-rag-on.{json,md}` / `latest-rag-off.{json,md}` — 모드별 최신 결과
- `compare-latest.md` — on/off 점수 비교표 (둘 다 실행 시)
- `runs/{timestamp}_{MODE}.{json,md}` — 실행 이력 (gitignore, 로컬 튜닝용)

수동 측정 기록 (자동화 이전):

- [`docs/RAG_EVAL_v1.md`](docs/RAG_EVAL_v1.md) — baseline
- [`docs/RAG_EVAL_v1.1.md`](docs/RAG_EVAL_v1.1.md) — FAQ chunk·prompt 개선
- [`docs/RAG_EVAL_v2.md`](docs/RAG_EVAL_v2.md) — RAG on vs off (자동 재현 기준)

## 한계

- Ollama 로컬 실행 필요 (외부 LLM API·클라우드 배포 없음)
- PDF는 텍스트 레이어만 지원 (스캔본 OCR 없음)
- DB schema migration은 repo에 없음 (로컬 수동 DDL)