# RAG 품질 비교 v2 (RAG on vs off)

동일 10문항 × **RAG on**(v1.1 기준) vs **RAG off**(LLM만) 대비.

## 문서 관계

| 문서 | 역할 |
| --- | --- |
| [`RAG_EVAL_v1.md`](RAG_EVAL_v1.md) | v1 baseline (동결) |
| [`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md) | RAG on 개선·재측정 (1·5·6·7·8) |
| **본 문서 (v2)** | RAG off 10문항 + on/off 비교 |

## 전제

| 항목 | RAG on | RAG off |
| --- | --- | --- |
| 코퍼스 | README, DECISIONS, ARCHITECTURE + `__SYSTEM_FAQ.md` | **미사용** (검색 없음) |
| Chat / Embedding | `qwen2.5:7b` / `nomic-embed-text` | `qwen2.5:7b` only |
| Retrieval | top-k 10, min-score 0.2 | **없음** |
| Temperature | 0 | 0 (동일) |
| 측정 API | `POST /api/chat` | `GET /api/debug/ollama/chat?prompt=...` (`local` 프로필) |
| Prompt | `PromptBuilder` + Context | system(한국어) + **질문만** |

## 점수 기준

v1과 동일. RAG off는 **프로젝트 문서·정책과의 일치**로 채점 (출처·`grounded`는 RAG off에서 항상 없음).

| 점수 | 기준 |
| --- | --- |
| 2 | 사실·정책 정확 (프로젝트 문서와 일치) |
| 1 | 부분 정확, 또는 언어/누락 |
| 0 | 오답, 일반론·환각, 질문과 반대/무관한 답 |

## 종합 (10문항 × 2모드)

| 구분 | 문항 | RAG on (v1.1) | RAG off | 비고 |
| --- | --- | --- | --- | --- |
| A. 문서 내 사실 | 1 ~ 4 | **8 / 8** | **0 / 8** | off: 일반론·모르겠다·타 모델 나열 |
| B. 문서 내 정책 | 5 ~ 7 | **6 / 6** | **0 / 6** | off: 추측·일반 UX·450/150 아님 |
| C. 문서 밖 (환각 방지) | 8 ~ 10 | **6 / 6** | **0 / 6** | off: no-answer 실패·환각/일반론 |
| **합계** | 1 ~ 10 | **20 / 20** | **0 / 20** | |

| 지표 | RAG on | RAG off |
| --- | --- | --- |
| 문서 내 정확 답 (1 ~ 7) | **7 / 7** | **0 / 7** |
| 문서 밖 no-answer (8 ~ 10) | **3 / 3** | **0 / 3** |
| 중국어 혼입 (5·6·8) | v1.1에서 5번 완화 | **3 / 3 재현** |

---

## 문항별 on/off

| # | 질문 | RAG on | RAG off | RAG off 이슈 |
| --- | --- | --- | --- | --- |
| 1 | 이 프로젝트의 기술 스택은? | **2** | **0** | React/Node/MySQL 등 **일반 웹 스택**; Java 17·Ollama·pgvector **없음** |
| 2 | Ollama base-url은? | **2** | **0** | 정보 부족·재질문; `http://localhost:11434` **미답** |
| 3 | chat / embedding model 이름은? | **2** | **0** | ChatGPT·Claude·BERT 등 **타 제품**; `qwen2.5:7b`·`nomic-embed-text` **없음** |
| 4 | pgvector 대신 Chroma를 쓰지 않은 이유? | **2** | **0** | **질문 반대**(Chroma 장점·PGVector 단점) 일반론 |
| 5 | Spring AI를 전면 도입하지 않은 이유? | **2** | **0** | 일반 추측; **중국어** 혼입·문장 단절 |
| 6 | 검색 hit가 없을 때 앱은 어떻게 동작하나? | **2** | **0** | 알림·추천검색어 등 **일반 UX**; hits empty→LLM 미호출 **없음**; **중국어** 꼬리 |
| 7 | chunk-size와 chunk-overlap은? | **2** | **0** | NLP 일반 설명(256/1 예시); **450 / 150** **없음** |
| 8 | 이 프로젝트의 2025년 매출은? | **2** | **0** | no-answer 아님; **중국어** 재질문 |
| 9 | Kubernetes로 배포했나? | **2** | **0** | K8s **일반론·가능**; 프로젝트 미배포 사실 **부정** |
| 10 | fine-tuning을 했나? | **2** | **0** | 모델 1인칭 “fine-tuning 거쳤다” **환각** |

RAG on 점수: v1.1(1·5·6·7·8) + v1(2 ~ 4·9 ~ 10) — v1.1에서 1·5·6·7은 2점 수준 달성.

---

## RAG off 답변 요약 (실측)

### 1번
일반 웹 프로젝트 스택(React, Node, MySQL, Docker/K8s 등) 나열. **본 프로젝트 스택 아님.**

### 2번
Ollama base-url 정보 부족, 명확화 요청.

### 3번
ChatGPT, Claude, BERT, SentenceTransformer 등 **대표 예시** — 프로젝트 모델명 없음.

### 4번
PGVector vs Chroma **일반 비교**. “Chroma를 쓰는 이유” 쪽으로 서술되어 **질문과 방향 불일치**.

### 5번
호환성·비용 등 **일반 추측** 후 중국어·깨진 문장.

### 6번
검색 결과 없을 때 알림·추천어 등 **일반 앱 UX**. RagService no-answer 정책 **미언급**.

### 7번
chunk-size/overlap **NLP 교과서** (256, overlap 1). 프로젝트 **450 / 150** 없음.

### 8번
매출 답 없음; 한국어 사과 + **중국어** 추가 정보 요청 — no-answer 문구 **아님**.

### 9번
“Kubernetes로 배포할 수 있다” **일반론**. 문서에 없는 사실을 **긍정하지 않음** but no-answer도 **아님**.

### 10번
“저는 fine-tuning을 거쳤다” **모델 자기 서술**. 프로젝트 fine-tuning 여부 **환각**.

---

## v2 한 줄 결론

**RAG off 0/20:** 문서 없이 LLM만 쓰면 프로젝트 사실(1 ~ 7)은 전부 빗나가고, 문서 밖(8 ~ 10)도 no-answer 대신 일반론·환각·다국어 혼입이 발생한다. **RAG on 20/20(v1.1 기준)** 과 대비해 “검색 + no-answer 정책”의 필요성이 수치로 드러난다.

## no-answer 비교 예 (질문 8번)

> 「2025년 매출?」— RAG off: 중국어 재질문 / RAG on: 「문서에서 확인할 수 없는 질문입니다.」

---

> 측정일: 2026-06 · RAG off: `GET /api/debug/ollama/chat` (local) · RAG on: [`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md)
