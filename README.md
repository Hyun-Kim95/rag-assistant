# RAG Assistant

> Spring Boot 기반 RAG 검색·생성 엔진 (hybrid + rerank, 출처 인용)

Ollama와 PostgreSQL pgvector로 업로드 문서를 검색해 답하는 Spring Boot RAG 백엔드입니다. 검색·생성·출처 인용 파이프라인을 REST API로 제공합니다.  
문서를 업로드하면 chunk → embedding → 검색 → 생성 순으로 처리하고, 답변과 함께 출처(sources)·`grounded` 여부를 반환합니다.

**스택:** Java 17, Spring Boot, Gradle, Ollama (`qwen2.5:7b`, `nomic-embed-text`), PostgreSQL + pgvector 
검색: hybrid(`pg_trgm` + RRF, `rag.hybrid-enabled` 기본 `true`) + rerank(TEI cross-encoder `bge-reranker-v2-m3`, `rag.rerank-enabled` 기본 `true`, 미기동 시 fallback) 
라우팅: Model Router — chat 추론을 다중 provider로 분기·폴백(Ollama primary + OpenAI 호환 SaaS leg, 예: Groq). 설정/요청 기준 라우팅, 실패 시 자동 폴백 ([`DECISIONS.md`](docs/DECISIONS.md) §15)

설계·선택 이유: [`docs/DECISIONS.md`](docs/DECISIONS.md) · API·DB·설정: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

## Demo

문서 업로드 후 RAG 질의·출처·no-answer 동작 (~45초 분량).

<img width="1216" height="742" alt="Image" src="https://github.com/user-attachments/assets/1e1830a6-3e13-4a6d-b255-7d9659e46fc5" />

## 실행

**필요:** JDK 17, Ollama (`http://localhost:11434`), PostgreSQL + pgvector, TEI reranker (`http://localhost:8085`, 기본 on·미기동 시 fallback)

> **의존성 등급:** Ollama·DB는 **필수**(없으면 동작 불가 → `/api/health` `DOWN`·503). TEI reranker는 **품질 의존성**으로, 미기동 시 fallback(원본 검색 순서)으로 **서비스는 계속 동작**하되 검색 품질만 떨어진다 → `/api/health`는 `DEGRADED`(200)로 표시.

> **(선택) 2nd chat provider:** Groq 등 OpenAI 호환 SaaS leg를 쓰려면 [console.groq.com](https://console.groq.com)에서 무료 키를 발급받아 `application-local.yml`에 `llm.openai-compat.enabled: true` + `api-key`를 넣는다. 미설정 시 Ollama 단독으로 동작한다. provider별 품질·지연 비교: `--rag.eval.providers=ollama-7b,groq` (아래 RAG 평가).

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
| `POST` | `/api/chat` | RAG 응답 (JSON). 선택 `provider` 지정 시 해당 leg 우선, 응답에 `provider` 포함 |
| `POST` | `/api/chat/stream` | RAG 스트리밍 (SSE). default 라우팅(요청 provider 미적용·폴백 없음) |
| `GET` | `/api/health` | 앱 + 의존성 상태. db DOWN 또는 chat provider 0개 UP → `DOWN`·503, reranker만 DOWN → `DEGRADED`·200. `dependencies`에 provider별 상태 |

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

- chat은 다중 provider 라우팅·폴백 지원(Ollama primary + OpenAI 호환 SaaS leg). 단 **임베딩은 Ollama 단일**(차원 768 고정)이라 Ollama 없이는 검색 단계가 동작하지 않음
- SaaS leg는 키 설정(`application-local.yml`) 시에만 활성. 헬스의 SaaS 상태는 config 수준(실제 도달성 핑 아님), 스트리밍은 폴백 없이 default leg만 사용
- 클라우드 배포 없음 (로컬 실행)
- PDF는 텍스트 레이어만 지원 (스캔본 OCR 없음)
- DB schema migration은 repo에 없음 (로컬 수동 DDL)