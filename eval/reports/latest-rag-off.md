# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_OFF |
| ranAt | 2026-06-27 16:27 |
| avgLatencyMs | 843 |
| total | **0 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 0/2 | false | 0 | false |
| 2 | A_FACT | 0/2 | false | 0 | false |
| 3 | A_FACT | 0/2 | false | 0 | false |
| 4 | A_FACT | 0/2 | false | 0 | false |
| 5 | B_POLICY | 0/2 | false | 0 | false |
| 6 | B_POLICY | 0/2 | false | 0 | false |
| 7 | B_POLICY | 0/2 | false | 0 | false |
| 8 | C_NO_ANSWER | 0/2 | false | 0 | false |
| 9 | C_NO_ANSWER | 0/2 | false | 0 | false |
| 10 | C_NO_ANSWER | 0/2 | false | 0 | false |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```

```

### 2. Ollama base-url은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
죄송합니다. 해당 문서에서 Ollama base‑url에 대한 정보를 찾을 수 없습니다.
```

### 3. chat / embedding model 이름은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
죄송합니다. 제공된 문서가 없어서 해당 질문에 대한 답변을 드릴 수 없습니다.
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에는 pgvector 대신 Chroma를 사용하지 않은 구체적인 이유가 명시되어 있지 않습니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
죄송합니다. 제공된 문서가 없으므로 해당 질문에 대해 답변드릴 수 없습니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
죄송합니다. 업로드된 문서에 해당 내용이 포함되어 있지 않아 정확히 답변드릴 수 없습니다.
```

### 7. chunk-size와 chunk-overlap은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
**chunk‑size**는 한 번에 처리되는 텍스트 조각(Chunk)의 길이, 즉 토큰 수를 말합니다.  
**chunk‑overlap**는 인접한 두 Chunk 사이에 중복되는 토큰 수를 의미합니다. 두 조각이 일정 부분을 공유하도록 설정해 문맥 상실을 최소화합니다.
```

### 8. 이 프로젝트의 2025년 매출은?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase

```
죄송합니다. 제공해 주신 문서가 없어서 2025년 매출에 대한 정보를 확인할 수 없습니다. 문서를 업로드해 주시면 도와드리겠습니다.
```

### 9. Kubernetes로 배포했나?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase

```
죄송합니다. 제공된 문서가 없어서 해당 질문에 대해 답변 드릴 수 없습니다.
```

### 10. fine-tuning을 했나?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase

```
죄송합니다. 해당 문서가 제공되지 않아 답변 드릴 수 없습니다.
```
