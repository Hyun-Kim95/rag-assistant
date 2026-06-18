# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| ranAt | 2026-06-18 17:49 |
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
Spring Boot, Ollama, PostgreSQL pgvector
```

### 2. Ollama base-url은?

**score:** 2/2

```
`application.yml` 파일에서 `ollama.base-url`의 값은 `http://localhost:11434`입니다.
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
pgvector 대신 Chroma를 사용하지 않은 이유는 다음과 같습니다.

- 별도 벡터 DB를 추가하지 않아도 됨
- Spring Boot + JDBC/JPA와 자연스럽게 연결
- 문서 메타데이터와 벡터를 한 DB에서 관리 가능
- 메타데이터 CRUD·벡터 검색을 같은 트랜잭션/연결 흐름으로 다루기 쉬움
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 2/2

```
Spring AI를 전면 도입하지 않은 이유는 다음과 같습니다.

- chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어하기 위함입니다.
- Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확해야 함으로, Spring AI 도입을 피했습니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 2/2

```
검색 hit가 없을 때 앱은 `grounded=false`, no-answer로 동작합니다. 문서에서는 이 경우 LLM 호출이 이루어지지 않으며, 최종 응답은 `grounded=false`이고 `sources`는 빈 배열입니다.
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
