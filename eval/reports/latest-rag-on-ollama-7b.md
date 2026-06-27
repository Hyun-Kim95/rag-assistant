# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | ollama-7b |
| ranAt | 2026-06-27 16:44 |
| avgLatencyMs | 61969 |
| total | **20 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 2/2 | true | 8 | false |
| 4 | A_FACT | 2/2 | true | 8 | false |
| 5 | B_POLICY | 2/2 | true | 8 | false |
| 6 | B_POLICY | 2/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 9 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 10 | C_NO_ANSWER | 2/2 | false | 0 | true |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
- Docker
- Ollama (Chat model: `qwen2.5:7b`, Embedding model: `nomic-embed-text`)
- pgvector
- Spring Boot 4, Spring Web 7
- WebSocket
```

### 2. Ollama base-url은?

**score:** 2/2

```
http://localhost:11434
```

### 3. chat / embedding model 이름은?

**score:** 2/2

```
- `qwen2.5:7b`
- `nomic-embed-text`
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 2/2

```
- RAG 입문에는 Chroma가 더 빠를 수 있음
- 이미 PostgreSQL이 있고, Spring Boot + JDBC와 맞추려면 pgvector가 더 적합
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
Spring AI를 전면 도입하지 않은 이유는 다음과 같습니다.

- chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어하기 위함입니다.
- Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확하게 남아있기 때문입니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 2/2

```
검색 hit가 없을 때 앱은 LLM을 호출하지 않고 no-answer를 반환한다.
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
chunk-size는 450, chunk-overlap는 150입니다.
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

**score:** 2/2

```
문서에서 확인할 수 없는 질문입니다.
```
