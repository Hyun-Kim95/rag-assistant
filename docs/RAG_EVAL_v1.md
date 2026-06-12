# RAG 품질 비교 v1 (baseline)

**개선 전 baseline** — 아래 점수·문항 표는 **v1 측정 당시 설정·코퍼스 기준으로 동결**한다. (FAQ chunk·top-k 10·prompt 보강 **이전**)

## 문서 관계

| 문서 | 역할 | 상태 |
| --- | --- | --- |
| **본 문서 (v1)** | baseline (14/18, 9문항) | 동결 — 수치 수정하지 않음 |
| [`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md) | RAG on 재측정 (개선 후) | **2026-06** |
| [`RAG_EVAL_v2.md`](RAG_EVAL_v2.md) | RAG on vs off (10문항) | **2026-06** |

**최신 on/off 비교는 v2.** v1은 baseline 동결, v1.1은 RAG on 개선.

## 전제

| 항목 | 값 |
| --- | --- |
| 코퍼스 | `README.md`, `DECISIONS.md`, `ARCHITECTURE.md` 업로드 |
| Chat model | `qwen2.5:7b` |
| Embedding model | `nomic-embed-text` |
| Retrieval | top-k 7, min-score 0.2 |
| 측정 API | RAG on: `POST /api/chat` |
| RAG off | **v1 미측정** → [`RAG_EVAL_v2.md`](RAG_EVAL_v2.md) |

## 점수 기준

| 점수 | 기준 |
| --- | --- |
| 2 | 사실·정책 정확 + 출처·`grounded` 일치 |
| 1 | 부분 정확, 또는 언어/누락 |
| 0 | 오답, retrieval miss로 인한 오탐 no-answer |

## 종합

| 구분 | 문항 | RAG on 합계 | 비고 |
| --- | --- | --- | --- |
| A. 문서 내 사실 | 1 ~ 4 | 7 / 8 | 1번 목록 누락 |
| B. 문서 내 정책 | 5 ~ 6 | 1 / 4 | 5번 중국어, 6번 불안정 |
| C. 문서 밖 (환각 방지) | 8 ~ 10 | 6 / 6 | no-answer 3/3 |
| **합계 (측정 9문항)** | — | **14 / 18 (78%)** | 7번 미실시 |

| 지표 | v1 결과 |
| --- | --- |
| 문서 밖 no-answer | **3 / 3** |
| 6번 동일 질문 4회 retrieval 성공 | **1 / 4 (25%)** |
| 5번 중국어 혼입 | **2 / 2 재현** |
| RAG off 비교 | **v2 완료** — [`RAG_EVAL_v2.md`](RAG_EVAL_v2.md) (0/20 vs on 20/20) |

## 문항별 (v1)

| # | 질문 | RAG on | `grounded` | 출처 | 이슈 |
| --- | --- | --- | --- | --- | --- |
| 1 | 이 프로젝트의 기술 스택은? | **1** | true | 7건 (0.67 ~ 0.73) | Spring Boot·Ollama만. Java 17·PostgreSQL·Gradle 누락 |
| 2 | Ollama base-url은? | **2** | true | DECISIONS 0.70 등 | `http://localhost:11434` |
| 3 | chat / embedding model 이름은? | **2** | true | DECISIONS 0.69 등 | `qwen2.5:7b`, `nomic-embed-text` |
| 4 | pgvector 대신 Chroma를 쓰지 않은 이유? | **2** | true | DECISIONS 0.75 등 | PostgreSQL 보유·메타+벡터 단일 DB |
| 5 | Spring AI를 전면 도입하지 않은 이유? | **1** | true | DECISIONS 0.72 등 | 내용 OK, **중국어 혼입 2/2** |
| 6 | 검색 hit가 없을 때 앱은 어떻게 동작하나? | **0** | 1회 true / 3회 false | 1회 7건 / 3회 없음 | **4회 중 1회만 retrieval 성공**. 성공 시에도 hits empty→LLM 미호출 설명 누락 |
| 7 | chunk-size와 chunk-overlap은? | **—** | — | — | **v1 미실시** |
| 8 | 이 프로젝트의 2025년 매출은? | **2** | false | 없음 | no-answer (의도대로) |
| 9 | Kubernetes로 배포했나? | **2** | false | 없음 | no-answer (의도대로) |
| 10 | fine-tuning을 했나? | **2** | false | 없음 | no-answer (의도대로) |

6번 점수는 데모 신뢰 기준(4회 중 3회 miss)으로 **0** 처리. retrieval 성공 1회만 내용상 최대 1점.

## 6번 상세 (동일 질문 4회)

| 시도 | retrieval | top score | 답변 | 점수 |
| --- | --- | --- | --- | --- |
| 1 | miss | — | no-answer (오탐) | 0 |
| 2 | hit | 0.64 | `grounded=false`, `sources=[]` 언급. LLM 경로만 설명 | 1 |
| 3 | miss | — | no-answer (오탐) | 0 |
| 4 | miss | — | no-answer (오탐) | 0 |

**메타 실패:** 정책 설명 질문인데 retrieval miss → `RagService`가 hits empty로 no-answer 반환.

## v1 한 줄 결론

문서 밖 질문(8 ~ 10)은 RAG no-answer 3/3로 환각 방지에 유효했다. 사실 조회(2 ~ 4)는 안정적(6/6점). 정책·나열(1, 5, 6)은 약했다: 기술 스택 누락(1), qwen2.5:7b 중국어 혼입(5), “hit 없을 때” 동일 질문 4회 중 retrieval 1회만 성공(6).

개선 후 재측정: [`RAG_EVAL_v1.1.md`](RAG_EVAL_v1.1.md) · RAG on/off: [`RAG_EVAL_v2.md`](RAG_EVAL_v2.md)

---

> 측정일: 2026-06 · v1 baseline (동결)
