# RAG Assistant

Ollama와 PostgreSQL pgvector로 문서를 검색해 답하는 Spring Boot RAG 데모입니다.  
문서를 업로드하면 chunk → embedding → 검색 → 생성 순으로 처리하고, 답변과 함께 출처(sources)·`grounded` 여부를 반환합니다.

**스택:** Java 17, Spring Boot, Gradle, Ollama (`qwen2.5:7b`, `nomic-embed-text`), PostgreSQL + pgvector 
(선택) hybrid 검색: `pg_trgm` + RRF — `rag.hybrid-enabled` 기본 `false`

설계·선택 이유: [`docs/DECISIONS.md`](docs/DECISIONS.md) · API·DB·설정: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

## Demo

문서 업로드 후 RAG 질의·출처·no-answer 동작을 보여주는 화면 녹화 (~45초).

[데모 영상]([docs/demo.mp4](https://github.com/user-attachments/assets/1fe22580-16c4-42b2-b2d3-990971f1a1db))
https://github.com/user-attachments/assets/1fe22580-16c4-42b2-b2d3-990971f1a1db
## 실행

**필요:** JDK 17, Ollama (`http://localhost:11434`), PostgreSQL + pgvector

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

1. PostgreSQL 준비 — 예: `pgvector/pgvector:pg16`, `localhost:15432`, DB `rag_assistant`  
   extension·테이블·인덱스는 수동 DDL ([`ARCHITECTURE.md`](docs/ARCHITECTURE.md) §6, hybrid 시 [`DECISIONS.md`](docs/DECISIONS.md) §11)
2. `cp application-local.yml.example application-local.yml` 후 DB 비밀번호 입력
3. `gradlew.bat bootRun` (또는 `./gradlew bootRun`)
4. http://localhost:8080 — 문서 업로드 후 채팅

Ollama·RAG 설정은 `src/main/resources/application.yml`만 수정합니다.

## API (핵심)

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/documents/upload` | 문서 업로드 + 인덱싱 |
| `GET` | `/api/documents` | 문서 목록 |
| `DELETE` | `/api/documents/{id}` | 문서 삭제 |
| `POST` | `/api/chat` | RAG 응답 (JSON) |
| `POST` | `/api/chat/stream` | RAG 스트리밍 (SSE) |
| `GET` | `/api/health` | 상태 확인 |

`local` 프로필: Swagger http://localhost:8080/swagger-ui.html, debug API (`/api/debug/...`)

## RAG 평가

동일 10문항으로 RAG on/off를 비교한 기록입니다.

- [`docs/RAG_EVAL_v1.md`](docs/RAG_EVAL_v1.md) — baseline
- [`docs/RAG_EVAL_v1.1.md`](docs/RAG_EVAL_v1.1.md) — FAQ chunk·prompt 개선
- [`docs/RAG_EVAL_v2.md`](docs/RAG_EVAL_v2.md) — RAG on vs off

## 한계

- Ollama 로컬 실행 필요 (외부 LLM API·클라우드 배포 없음)
- PDF는 텍스트 레이어만 지원 (스캔본 OCR 없음)
- DB schema migration은 repo에 없음 (로컬 수동 DDL)
