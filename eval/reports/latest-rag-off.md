# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_OFF |
| ranAt | 2026-06-17 23:58 |
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
문서에 기술된 내용을 바탕으로 답변드리겠습니다. 하지만 현재 제공된 정보에는 해당 프로젝트의 기술 스택에 대한 내용이 없습니다. 다른 질문이나 문서 정보를 제공해 주시면 도와드릴 수 있습니다.
```

### 2. Ollama base-url은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에 해당 정보가 포함되어 있지 않습니다. Ollama base-url과 관련된 정확한 정보를 제공해 주실 수 있나요?
```

### 3. chat / embedding model 이름은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
업로드된 문서에 따르면 chat 및 embedding 모델의 명칭은 직접 언급되지 않았습니다. 관련 정보를 찾 若要获取相关信息，您需要提供更多的上下文或文档内容。当前根据提供的信息无法确定具体的聊天模型和嵌入模型名称。您可以上传相关文档或提供更多详细信息以获得更准确的答案。
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
PGVector와 Chroma 둘 다 벡터 저장 및 검색을 위한 도구로 사용되지만, 특정 프로젝트에서 PGVector 대신 Chroma를 사용하지 않는 이유는 다음과 같습니다:

1. **프로젝트 요구사항**: PGVector가 필요한 기능을 제공하지 않을 수 있습니다.
2. **커뮤니티와 지원**: Chroma의 커뮤니티나 문서가 더 발달되어 있을 수 있습니다.
3. **성능 차이**: 특정 작업에서 Chroma가 더 빠르거나 효율적일 수 있습니다.
4. **인터페이스 및 사용 편의성**: 개발자가 Chroma를 더 선호할 수 있습니다.

확실한 이유는 문서나 상황에 따라 다릅니다.
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에 따르면 Spring AI를 전면 도입하지 않은 이유는 명확히 언급되어 있지 않습니다. 더 구체적인 내용이나 문맥이 필요합니다. 문서에서 제공된 정보만으로는 정확한 답변을 드리기 어렵습니다. 관련된 추가 정보나 문맥을 알려주시면 감사하겠습니다.
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
문서에 관련 정보가 없어 정확한 답변을 드릴 수 없습니다. 앱의 동작 방식은 해당 앱의 설계와 구조에 따라 다릅니다. 일반적으로 검색 hit가 없을 때는 다음과 같은 방법으로 동작할 수 있습니다:

1. 사용자에게 다른 관련 검색어를 제안합니다.
2. 기본 정보 또는 일반적인 답변을 제공합니다.
3. 오류 메시지를 표시하고 사용자와 의사소통을 시도합니다.

확실한 답변을 드리기 위해서는 해당 앱의 문서나 설계서를 참조해야 합니다.
```

### 7. chunk-size와 chunk-overlap은?

**score:** 0/2

**failReasons:** mustGrounded but grounded=false, sources below minSourceCount

```
`chunk-size`와 `chunk-overlap`은 텍스트를 처리하거나 분석할 때 사용되는 매개변수입니다.

- **chunk-size**: 한 번에 처리하는 텍스트의 크기를 지정합니다. 예를 들어, 문서를 100단어씩 나누는 경우 chunk-size는 100을 의미합니다.
  
- **chunk-overlap**: 각 채크가 다음 채크와 얼마나 겹치는지를 나타냅니다. 예를 들어, chunk-overlap이 50인 경우에는 각 채크가 50단어씩 겹칩니다.

이 매개변수들은 텍스트 분석이나 자연어 처리 작업에서 데이터의 연속성과 효율성을 높이는 데 도움을 줍니다.
```

### 8. 이 프로젝트의 2025년 매출은?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase

```
문서에 있는 정보를 바탕으로 2025년 매출을 알려드리겠습니다. 하지만 현재 제공된 정보는 부족하여 정확한 답변을 드릴 수 없습니다. 관련 데이터나 분석 결과가 포함된 문서를 확인해 주시면 더 정확한 답변을 드릴 수 있습니다.
```

### 9. Kubernetes로 배포했나?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase, forbidden: 배포했

```
문서에 따르면 Kubernetes를 사용하여 애플리케이션을 배포했습니다.
```

### 10. fine-tuning을 했나?

**score:** 0/2

**failReasons:** expectNoAnswer but answer is not no-answer phrase

```
문서에 대한 정보가 없습니다. fine-tuning 여부를 판단하기 위해서는 관련된 내용이 필요합니다. 구체적인 문건이나 상황을 알려주실 수 있나요?
```
