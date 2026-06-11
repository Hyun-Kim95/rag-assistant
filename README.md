# RAG Assistant

로컬 LLM(Ollama)과 PostgreSQL pgvector로 문서 기반 Q&A를 구현하는 Spring Boot 프로젝트입니다.
문서를 업로드하고, 검색된 근거와 출처를 바탕으로 답변합니다.

## 목적

- 외부 LLM API 없이 로컬에서 RAG 파이프라인 학습·실험
- 업로드 → chunking → embedding → 검색 → 생성 → 출처 표시

## 기술 스택

Java 17 · Spring Boot · Ollama · PostgreSQL(pgvector) · Gradle

## 접속
| 용도 | URL |
| --- | --- |
| UI | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/api/health |

## License

Personal portfolio project.
