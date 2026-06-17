# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| ranAt | 2026-06-18 01:26 |
| total | **16 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 10 | false |
| 2 | A_FACT | 2/2 | true | 10 | false |
| 3 | A_FACT | 2/2 | true | 10 | false |
| 4 | A_FACT | 2/2 | true | 10 | false |
| 5 | B_POLICY | 0/2 | false | 0 | true |
| 6 | B_POLICY | 0/2 | false | 0 | true |
| 7 | B_POLICY | 2/2 | true | 10 | false |
| 8 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 9 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 10 | C_NO_ANSWER | 2/2 | false | 0 | true |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
- Spring Boot
- Docker
- PostgreSQL
- pgvector
- Swagger
```

### 2. Ollama base-url은?

**score:** 2/2

```
http://localhost:11434
```

### 3. chat / embedding model 이름은?

**score:** 2/2

```
`chat model`은 `qwen2.5:7b`, `embedding model`은 `nomic-embed-text`입니다.
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 2/2

```
- 별도 벡터 DB를 추가하지 않아도 됨
- Spring Boot + JDBC/JPA와 자연스럽게 연결
- 문서 메타데이터와 벡터를 한 DB에서 관리 가능
- 메타데이터 CRUD·벡터 검색을 같은 트랜잭션/연결 흐름으로 다루기 쉬움
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에서 확인할 수 없는 질문입니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에서 확인할 수 없는 질문입니다.
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
