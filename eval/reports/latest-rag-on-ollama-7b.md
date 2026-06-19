# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | ollama-7b |
| ranAt | 2026-06-19 17:40 |
| avgLatencyMs | 11424 |
| total | **19 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 2/2 | true | 8 | false |
| 4 | A_FACT | 2/2 | true | 8 | false |
| 5 | B_POLICY | 2/2 | true | 8 | false |
| 6 | B_POLICY | 1/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 9 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 10 | C_NO_ANSWER | 2/2 | false | 0 | true |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
- Spring Boot
- Ollama
- PostgreSQL pgvector

`RAG EVAL_v1.md`, `RAG_EVAL_v1.1.md`, `RAG_EVAL_v2.md` 문서에서 확인할 수 있습니다.
```

### 2. Ollama base-url은?

**score:** 2/2

```
Ollama base-url은 `http://localhost:11434`입니다.
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
`DECISIONS.md`에 따르면, pgvector 대신 Chroma를 사용하지 않은 이유는 별도 벡터 DB를 추가하지 않아도 됨, Spring Boot + JDBC/JPA와 자연스럽게 연결 가능, 그리고 문서 메타데이터와 벡터를 한 DB에서 관리할 수 있기 때문입니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
Spring AI를 전면 도입하지 않은 이유는 chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어하기 위함입니다. 또한, Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확해야 함으로써 관리의 용이성을 높이기 위해서입니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 1/2

```
검색 hit가 없을 때 앱은 no-answer로 동작합니다. `RagService`에서 검색-hit가 없는 경우 `done` 응답으로 no-answer를 반환합니다.
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
