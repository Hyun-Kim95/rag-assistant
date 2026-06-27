# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | groq |
| ranAt | 2026-06-27 16:50 |
| avgLatencyMs | 1465 |
| total | **18 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 0/2 | false | 0 | true |
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

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에서 확인할 수 없는 질문입니다.
```

### 2. Ollama base-url은?

**score:** 2/2

```
`http://localhost:11434`
```

### 3. chat / embedding model 이름은?

**score:** 2/2

```
- Chat 모델: `qwen2.5:7b`  
- Embedding 모델: `nomic-embed-text`
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 2/2

```
Chroma를 쓰지 않은 이유는 다음과 같습니다.  
- 별도 벡터 DB를 추가하지 않아도 되므로 인프라가 단순해집니다.  
- Spring Boot + JDBC/JPA와 자연스럽게 연결돼 메타데이터와 벡터를 같은 DB에서 관리할 수 있습니다.  
- 메타데이터 CRUD와 벡터 검색을 같은 트랜잭션·연결 흐름으로 다루기 쉽습니다.  
- 이미 PostgreSQL이 존재하고, pgvector가 Spring Boot와 JDBC에 더 적합하기 때문입니다.  
- RAG 입문 단계에서는 Chroma가 더 빠를 수 있으나, 현재 환경에서는 pgvector가 더 적합하다고 판단했습니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
Spring AI를 전면 도입하지 않은 이유는 RAG 파이프라인(분할·검색·프롬프트·no‑answer)을 프레임워크 추상화 없이 직접 구현함으로써 코드에서 각 단계의 동작을 명확히 제어하고, Ollama 연동, 검색 파라미터, no‑answer 정책을 변경할 때 수정 지점을 한 곳에 집중시키기 위함입니다. (DECISIONS.md)
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 2/2

```
검색 hit이 없으면 LLM을 호출하지 않고 바로 **no‑answer**를 반환합니다. 이때 `grounded=false`이며 `sources`는 빈 배열이 됩니다. (ARCHITECTURE.md)
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
chunk-size는 450, chunk-overlap은 150입니다.
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
