# RAG Assistant

**Local Document Q&A with Spring Boot, Ollama, and pgvector**

외부 LLM API 없이 로컬 환경에서 문서를 업로드하고, 검색된 근거를 바탕으로 출처가 포함된 답변을 받을 수 있는 RAG 기반 Q&A 서비스입니다.

## Why this project

- 로컬 LLM(Ollama)로 프라이빗한 문서 Q&A 구현
- PostgreSQL pgvector로 별도 벡터 DB 없이 검색 파이프라인 구성
- RAG 적용 전/후 응답 품질 비교
- 문서에 없는 질문에 대한 no-answer 처리
- 출처 문서/문단 기반 grounded response 제공

## Tech Stack

- Java 17
- Spring Boot
- Ollama
- PostgreSQL + pgvector
- Gradle

## Prerequisites

- Java 17+
- Docker Ollama (`http://localhost:11434`)
- PostgreSQL with pgvector extension (Week 1 Day 3+)

### Ollama models

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

## Before first commit

See [docs/PRE_COMMIT_CHECKLIST.md](docs/PRE_COMMIT_CHECKLIST.md) before pushing to GitHub.

## Getting Started

### 1. Clone

```bash
git clone <your-repo-url>
cd rag-assistant
```

### 2. Configure

Edit `src/main/resources/application.yml` if your Ollama URL or models differ.

### 3. Run

Windows:

```bash
gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

### 4. Health check

```bash
curl http://localhost:8080/api/health
```

### 5. Ollama debug endpoints

```bash
curl http://localhost:8080/api/debug/ollama/chat?prompt=Hello
curl http://localhost:8080/api/debug/ollama/embed?text=hello
```

## Project Status

- [x] Day 1: project scaffold, health check, Ollama connection test
- [ ] Day 2: document upload and parsing
- [ ] Day 3: chunking, embedding, pgvector storage
- [ ] Day 4: retrieval, prompt, chat API
- [ ] Day 5: minimal UI and first demo

## License

Personal portfolio project.
