# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | groq |
| ranAt | 2026-06-21 22:02 |
| avgLatencyMs | 1077 |
| total | **16 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 0/2 | true | 8 | false |
| 4 | A_FACT | 2/2 | true | 8 | false |
| 5 | B_POLICY | 2/2 | true | 8 | false |
| 6 | B_POLICY | 2/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 9 | C_NO_ANSWER | 2/2 | false | 0 | true |
| 10 | C_NO_ANSWER | 0/2 | true | 8 | false |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
Spring Boot, Ollama, PostgreSQL pgvector.
```

### 2. Ollama base-url은?

**score:** 2/2

```
DECISIONS.md에 따르면 Ollama base-url은 http://localhost:11434입니다.
```

### 3. chat / embedding model 이름은?

**score:** 0/2

```
chat / embedding 모델명은 실제 pull한 이름과 일치해야 함.
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 2/2

```
pgvector 대신 Chroma를 쓰지 않은 이유는 문서 메타데이터와 벡터를 한 DB에서 관리할 수 있고, Spring Boot + JDBC/JPA와 자연스럽게 연결되기 때문입니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어하기 때문에 Spring AI를 전면 도입하지 않았습니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 2/2

```
검색 hit이 없을 때 앱은 LLM 호출 전 no-answer로 처리합니다.
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
현재 chunk-size는 450, chunk-overlap은 150입니다.
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

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
fine-tuning은 문서에 명시되지 않았습니다.
```
