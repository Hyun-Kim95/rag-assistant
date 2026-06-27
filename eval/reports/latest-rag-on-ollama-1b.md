# RAG Eval Report

| 항목 | 값 |
|---|---|
| version | v2 |
| mode | RAG_ON |
| provider | ollama-1b |
| ranAt | 2026-06-27 16:45 |
| avgLatencyMs | 8424 |
| total | **11 / 20** |

| # | category | score | grounded | sources | noAnswer |
|---:|---|---:|---|---:|---|
| 1 | A_FACT | 2/2 | true | 8 | false |
| 2 | A_FACT | 2/2 | true | 8 | false |
| 3 | A_FACT | 2/2 | true | 8 | false |
| 4 | A_FACT | 0/2 | true | 8 | false |
| 5 | B_POLICY | 1/2 | true | 8 | false |
| 6 | B_POLICY | 2/2 | true | 8 | false |
| 7 | B_POLICY | 2/2 | true | 8 | false |
| 8 | C_NO_ANSWER | 0/2 | true | 8 | false |
| 9 | C_NO_ANSWER | 0/2 | true | 8 | false |
| 10 | C_NO_ANSWER | 0/2 | true | 8 | false |

## 문항별

### 1. 이 프로젝트의 기술 스택은?

**score:** 2/2

```
이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**: Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**: Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**: RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**: WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**: DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
	* Chat UI는 단순 챗 UI가 아니라 검색 + 생성 + 출처 인용 파이프라인이 핵심입니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
	* embedding model은 Windows 로컬 개발에 적합하고 OpenAI-compatible API로 Spring에서 연동이 가능합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**:
	* DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**:
	* DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**:
	* DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**:
	* DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를 사용하여 스트리밍을 구현합니다.
5. **DB**:
	* DocumentDB 또는 PostgreSQL을 사용하여 문서의 정보를 저장합니다.

이 프로젝트의 기술 스택은 다음과 같습니다.

1. **Chat UI**:
	* Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현합니다.
2. **LLM/Embedding**:
	* Ollama에서 사용하는 LLM API와 embedding model을 사용하여 문서 기반 Q&A를 구현합니다.
3. **API**:
	* RAG API를 구현하여 문서 기반 Q&A를 제공합니다.
4. **WebSockets**:
	* WebSocket 이벤트를
```

### 2. Ollama base-url은?

**score:** 2/2

```
`http://localhost:11434`

```json
{
  "status": "UP",
  "service": "ollama",
  "dependencies": {
    "ollama": "UP",
    "database": "UP",
    "reranker": "UP"
  }
}
```

`ollama`의 base-url은 `http://localhost:11434`입니다.
```

### 3. chat / embedding model 이름은?

**score:** 2/2

```
1. **chat** / **embedding model** 이름은? 
   - `qwen2.5:7b` 

2. **chat** / **embedding model** 이름은? 
   - `nomic-embed-text`

3. **chat** / **embedding model** 이름은? 
   - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

4. **chat** / **embedding model** 이름은? 
   - `qwen2.5:7b` 

5. **chat** / **embedding model** 이름은? 
   - `nomic-embed-text`

6. **chat** / **embedding model** 이름은? 
   - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

7. **chat** / **embedding model** 이름은? 
   - `qwen2.5:7b` 

8. **chat** / **embedding model** 이름은? 
   - `nomic-embed-text`

9. **chat** / **embedding model** 이름은? 
   - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

10. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

11. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

12. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

13. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

14. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

15. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

16. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

17. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

18. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

19. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

20. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

21. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

22. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

23. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

24. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

25. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

26. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

27. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

28. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

29. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

30. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

31. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

32. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

33. **chat** / **embedding model** 이름은? 
    - `llama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현한다.`

34. **chat** / **embedding model** 이름은? 
    - `qwen2.5:7b` 

35. **chat** / **embedding model** 이름은? 
    - `nomic-embed-text`

36. **chat** / **embedding model** 이름은? 
    - `
```

### 4. pgvector 대신 Chroma를 쓰지 않은 이유?

**score:** 0/2

```
Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할 수 있습니다. pgvector는 이러한 특성을 가지고 있기 때문에 RAG 프로젝트에서 사용하는 경우에 유용합니다.

Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할 수 있습니다. pgvector는 이러한 특성을 가지고 있기 때문에 RAG 프로젝트에서 사용하는 경우에 유용합니다.

Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할 수 있습니다. pgvector는 이러한 특성을 가지고 있기 때문에 RAG 프로젝트에서 사용하는 경우에 유용합니다.

Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할 수 있습니다. pgvector는 이러한 특성을 가지고 있기 때문에 RAG 프로젝트에서 사용하는 경우에 유용합니다.

Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할 수 있습니다. pgvector는 이러한 특성을 가지고 있기 때문에 RAG 프로젝트에서 사용하는 경우에 유용합니다.

Chroma 대신 pgvector를 사용한 이유는 다음과 같습니다.

1.  **대형 모델** : pgvector는 대형 모델을 쉽게 관리할 수 있는 벡터 DB로, RAG (Rapid Application Development) 프로젝트에서 사용하는 경우에 유용합니다.
2.  **기존의 테이블** : pgvector는 existing tables를 쉽게 관리할 수 있습니다. 예를 들어, `documents` 테이블은 `document_embeddings` 테이블과 연관된 테이블입니다.
3.  **RAG의 특성** : RAG 프로젝트에서 사용하는 경우에, 테이블을 쉽게 modify하고, query를 쉽게 작성할
```

### 5. Spring AI를 전면 도입하지 않은 이유?

**score:** 1/2

```
Spring AI를 전면 도입하지 않은 이유는 다음과 같습니다.

1.  **Lack of technical expertise**: Spring AI는 전문적인 기술자에게만 개발이 가능합니다. 따라서, Spring AI를 전면 도입하기 위해 전문적인 기술자에게 도움을 필요하거나, 기술 자격이 부족한 사람에게 도움이 필요할 수 있습니다.
2.  **Lack of experience with LLM**: Spring AI는 LLM (Language Model)와 관련된 기술에 대한 경험은 부족합니다. 따라서, Spring AI를 전면 도입하기 위해 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI는 데이터 preprocessing에 대한 전문적인 기술을 필요로 합니다. 따라서, Spring AI를 전면 도입하기 위해 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI를 개발하고 유지하는 데 필요한 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI를 개발하고 유지하는 데 필요한 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI를 개발하고 유지하는 데 필요한 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI를 개발하고 유지하는 데 필요한 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**: Spring AI를 개발하고 유지하는 데 필요한 데이터 preprocessing에 대한 전문적인 기술을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.

Spring AI를 전면 도입하기 위해서는 다음과 같은 단계가 필요합니다.

1.  **Lack of technical expertise**: Spring AI를 개발하고 유지하는 데 필요한 기술을 배울 수 있는 교육 및 훈련을 제공해야 합니다.
2.  **Lack of experience with LLM**: Spring AI를 개발하고 유지하는 데 필요한 LLM와 관련된 기술에 대한 경험을 얻기 위해 많은 시간과 노력이 필요할 수 있습니다.
3.  **Lack of expertise in data preprocessing**:
```

### 6. 검색 hit가 없을 때 앱은 어떻게 동작하나?

**score:** 2/2

```
`문서에서 확인할 수 없는 질문입니다.`는 `Context`에 있는 정보가 없을 때만 단독 출력합니다. 

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

이 예외를 통해, `noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`no-answer` 패턴이면 `grounded=false`, `sources[]`로 정렬합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`no-answer` 패턴이면 `LLM_NO_ANSWER`로 정렬됩니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`no-answer` 패턴이면 `grounded=false`, `sources[]`로 정렬합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`no-answer` 패턴이면 `LLM_NO_ANSWER`로 정렬됩니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`no-answer` 패턴이면 `grounded=false`, `sources[]`로 정렬합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`no-answer` 패턴이면 `LLM_NO_ANSWER`로 정렬됩니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`no-answer` 패턴이면 `grounded=false`, `sources[]`로 정렬합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`no-answer` 패턴이면 `LLM_NO_ANSWER`로 정렬됩니다. 

```python
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=false"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER`로 정렬되는 질문을 제거합니다.

```python
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
```

`noAnswerReason` 필터가 사용되어 `LLM_NO_ANSWER
```

### 7. chunk-size와 chunk-overlap은?

**score:** 2/2

```
chunk-size는 450, chunk-overlap는 150입니다.
```

### 8. 이 프로젝트의 2025년 매출은?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
이 프로젝트의 목표는 Ollama와 pgvector로 업로드 문서 기반 Q&A(RAG)를 구현하는 것입니다. 

### Ollama

- Ollama는 LLM (Language Model)입니다.
- Ollama는 chat UI를 제공합니다.
- Ollama는 search + generate + source 인용 파이프라인을 사용합니다.

### RAG

RAG은 문서 기반 Q&A 시스템입니다.
- RAG는 documents와 chunks의 relationship을 사용합니다.
- RAG는 search, generate, and source 인용을 사용합니다.

### LLM / Embedding

Ollama는 embeddings을 사용하여 documents를 embed합니다. 

### API / UX

API는 Ollama와 pgvector로 구현됩니다.
- API는 search, generate, and source 인용을 제공합니다.
- API는 chat UI를 제공합니다.

### RAG 질의응답

RAG의 질의 응답은 다음과 같습니다.

*   `Retriever`: 질문 embed → EmbeddingRepository.searchSimilar (cosine, top-k) → min-score 필터
*   `PromptBuilder`: `[규칙]:`·출구 1·2·3·`[Context]` user 프롬프트; `NO_ANSWER_MESSAGE` = "문서에서 확인할 수 없는 질문입니다."; Context는 `[출처: 문서명]`만 LLM에 전달

### RAG 질의응답 (2026-06)

RAG의 질의 응답은 다음과 같습니다.

*   선택
    *   `Retriever`: 질문 embed → EmbeddingRepository.searchSimilar (cosine, top-k) → min-score 필터
    *   `PromptBuilder`: `[규칙]:`·출구 1·2·3·`[Context]` user 프롬프트; `NO_ANSWER_MESSAGE` = "문서에서 확인할 수 없는 질문입니다."; Context는 `[출처: 문서명]`만 LLM에 전달
*   RAG 질의응답 (2026-06)
    *   선택
        *   `Retriever`: 질문 embed → EmbeddingRepository.searchSimilar (cosine, top-k) → min-score 필터
        *   `PromptBuilder`: `[규칙]:`·출구 1·2·3·`[Context]` user 프롬프트; `NO_ANSWER_MESSAGE` = "문서에서 확인할 수 없는 질문입니다."; Context는 `[출처: 문서명]`만 LLM에 전달
```

### 9. Kubernetes로 배포했나?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
RAG (Q&A RAG Assistant) 프로젝트는 Ollama와 pgvector로 업로드 문서 기반 Q&A를 구현하는 것을 목표로 한다. 

### 1. 프로젝트 목표

*   Ollama와 pgvector로 업로드 문서 기반 Q&A를 구현한다.
*   단순 챗 UI가 아니라 검색 + 생성 + 출처 인용 파이프라인이 핵심
*   외부 LLM API 없이 로컬 환경에서 동작

### 2. LLM / Embedding: Ollama

[출처: ARCHITECTURE.md]
ents","arguments":{...}}` | 도구 호출 시작 |
| `step` | `{"index":1,"phase":"tool_result","tool":"search_documents","resultSummary":"8 sources"}` | 도구 결과
| `delta` | `{"text":"..."}` | 최종답 토큰(청크) | 

### 3. API / UX 정책

*   검색된 context만 근거로 답변
*   context가 없거나 score가 낮으면 **no-answer**
*   가능하면 **출처 문서명 / snippet** 포함
*   API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일

### 4. Chunking 정책 (2026-06)

*   RAG 입문에는 Chroma가 더 빠를 수 있음
*   **이미 PostgreSQL이 있고**, Spring Boot + JDBC와 맞추려면 pgvector가 더 적합

### 5. Spring AI 사용 여부

*   RAG 파이프라인(chunking · retrieval · prompt · no-answer) **직접 구현**
*   Ollama 호출: `RestClient`로 HTTP 연동
*   **Spring AI 미사용**

### 6. Chunking 정책 (2026-06)

*   chunking / retrieval / prompt / no-answer를 프레임워크 추상화 없이 코드에서 직접 제어
*   Ollama 연동·검색 파라미터·no-answer 정책 변경 시 수정 지점이 명확함

### 7. API / UX 정책

*   검색된 context만 근거로 답변
*   context가 없거나 score가 낮으면 **no-answer**
*   가능하면 **출처 문서명 / snippet** 포함
*   API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일
```

### 10. fine-tuning을 했나?

**score:** 0/2

**failReasons:** expectNoAnswer but grounded=true, expectNoAnswer but sources not empty, expectNoAnswer but answer is not no-answer phrase, sources above maxSourceCount

```
이 Q&A 어시스턴트는 한국어로 답하는 한국어 Q&A 어시스턴트입니다. 

**Q&A**

1. **문서에서 확인할 수 없는 질문입니다.**
   - [Context]에 없으면 "문서에는 ○○에 대한 내용이 없습니다."로 짧게 말합니다.
2. **partially-found 질문**
   - [Context]에 여러 해석이 가능할 때, [Context]에 근거한 해석 1~2개를 짧게 적고, "○○인지 △△인지 구체적으로 질문해 주세요."로 끝내세요.
3. **chunk-based 답변**
   - chunk가 많다고 질문과 무관한 내용으로 답하지 마세요.
4. **no-answer**
   - [Context]가 없거나 score가 낮으면 **no-answer**로답합니다.
5. **API/UX 정책**

### 응답 정책

- 검색된 context만 근거로 답변
-context가 없거나 score가 낮으면 **no-answer**
- 가능하면 **출처 문서명 / snippet** 포함
- API 오류는 `ErrorResponse(error, message)` + HTTP 상태 코드로 통일

### 초기 UI

- 먼저 API 완성
- 이후 간단한 업로드 + 채팅 UI 추가

### Chunking 정책 (2026-06)

[출처: DECISIONS.md]
·라우팅을 **지금 붙이지 않음**.
- **이유:** 모델 호출 지점을 한 곳으로 모아 교체·확장 seam을 만든다. 확장 시 새 구현체(예: `RoutingChatModelClient`)를 `@Primary`로 추가하고 빈 와이어링만 바꾼다 → 소비자 코드 불변.
- **분리 단위:** chat·embedding은 모델·스케일·라우팅 관심사가 달라 인터페이스를 둘로 나눔(chat만 외부로 빼고 embedding은 로컬 유지 같은 시나리오 대비). dimension 검증 등 RAG 정책은 인터페이스가 아니라 `EmbeddingService`에 유지.

### Streaming Response (2026-06)

[출처: ARCHITECTURE.md]
저 UI (`static/`), Swagger, `local` 프로필 debug API
- RAG 평가 자동화: `eval/questions.json` 고정 세트 → `RagEvalRunner`(CLI 플래그) → `eval/reports/` JSON·MD (§11)
- RAG 품질 기록: [`RAG_EVAL_v1.md`](RAG_EVAL_v1.md) · [`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md) · [`RAG_EVAL_v2.md`](RAG_EVAL_v2.md)

### 예외 처리·문서 정책 (2026-06)

### Ollama 오류

- `OllamaService.postForMap()`: chat/embed 공통 HTTP 호출
- 연결 실패 (`ResourceAccessException`) → 503 `OLLAMA_UNAVAILABLE`

[출처: README.md]
wn 리포트로 남깁니다.

```bash
# RAG on (검색 + no-answer 정책)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
# RAG off (LLM만, 대조군)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_OFF"
# provider 비교 (한 번에execute → compare-providers.md 생성)
gradlew.bat bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON --rag.eval.providers=ollama-7b,ollama-1b,groq"
```

[Question]
fine-tuning을 했나?
[Answer]
```
```
