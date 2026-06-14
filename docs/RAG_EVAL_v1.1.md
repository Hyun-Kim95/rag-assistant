# RAG 품질 비교 v1.1 (retrieval/생성 품질 1차)

v1 baseline(`[RAG_EVAL_v1.md](RAG_EVAL_v1.md)`) 대비 **1·5·6·7·8번** 재측정·회귀 확인.
9 ~ 10번은 **v1 no-answer 결과 유지**(8번 회귀로 top-k 10·FAQ 이후 경로 확인). **RAG off:** `[RAG_EVAL_v2.md](RAG_EVAL_v2.md)`.

> **코드 메모:** v1.1 작성 시 `RagService.isNoAnswer()`로 기록됨. 현재 코드는 **`isGrounded()`**(동일 no-answer 판별 역할). 프롬프트 2차 개편·FAQ 문구 정리 후 회귀는 본 문서 말미 §참고.

## 전제


| 항목               | v1                                | v1.1                                   |
| ---------------- | --------------------------------- | -------------------------------------- |
| 코퍼스              | README, DECISIONS, ARCHITECTURE   | **동일** + 기동 시 `__SYSTEM_FAQ.md` 자동 인덱싱 |
| Chat / Embedding | `qwen2.5:7b` / `nomic-embed-text` | 동일 + `**ollama.temperature: 0`**       |
| Retrieval        | top-k 7, min-score 0.2            | **top-k 10**, min-score 0.2            |
| README           | 업로드본 짧음(327자)                     | **최신 README 재업로드** (`## 기술 스택` 포함)     |
| 측정 API           | `POST /api/chat`                  | 동일                                     |


## 적용한 개선 (누적)


| 구분                        | 내용                                                                        |
| ------------------------- | ------------------------------------------------------------------------- |
| FAQ chunk                 | `FaqBootstrap` + `FaqCatalog` — 정책·chunk 설정 chunk                         |
| `PromptBuilder`           | no-answer 단독 출력 규칙; **나열/스택** 질문 규칙; Context 밖 기술 금지                      |
| `RagService.isGrounded()` | v1.1 시 `isNoAnswer()`로 기록; no-answer **시작** + 짧은 꼬리(+40자 이내) → `grounded=false` |
| `OllamaService`           | `options.temperature: 0`                                                  |
| Retrieval                 | `rag.top-k: 7 → 10`                                                       |
| Corpus                    | README 재업로드·재인덱싱                                                          |


## 점수 기준

v1과 동일.


| 점수  | 기준                                  |
| --- | ----------------------------------- |
| 2   | 사실·정책 정확 + 출처·`grounded` 일치         |
| 1   | 부분 정확, 또는 언어/누락                     |
| 0   | 오답, retrieval miss로 인한 오탐 no-answer |


## 종합 (v1 → v1.1)


| #       | 질문                          | v1                    | v1.1                                  | 비고                                      |
| ------- | --------------------------- | --------------------- | ------------------------------------- | --------------------------------------- |
| 1       | 이 프로젝트의 기술 스택은?             | **1**                 | **8 / 8** (4회×2점)                     | top-k 10 + README + prompt              |
| 5       | Spring AI를 전면 도입하지 않은 이유?   | **1** (중국어 2/2)       | **7 / 8** (4회)                        | 중국어 혼입 **0/4**                          |
| 6       | 검색 hit가 없을 때 앱은 어떻게 동작하나?   | **0** (1/4 retrieval) | **8 / 8** (4회) + top-k 10 후 **1회 확인** | FAQ + `isGrounded()`                    |
| 8       | 이 프로젝트의 2025년 매출은?          | **2**                 | **2** (1회)                            | top-k 10 **회귀 없음**                      |
| 7       | chunk-size와 chunk-overlap은? | —                     | **8 / 8** (4회)                        | FAQ + DECISIONS chunk                   |
| 9 ~ 10    | 문서 밖                        | **4 / 4** (v1)        | **v1 유지**                             | 8번 회귀로 no-answer 경로 확인; 9·10 **재측정 생략** |
| RAG off | —                           | **0 / 20**            | `[RAG_EVAL_v2.md](RAG_EVAL_v2.md)`    |                                         |


---

## 1번 — 기술 스택 (4회, top-k 10)

**질문:** 이 프로젝트의 기술 스택은?


| 시도  | 답변 요약                                                                    | README 출처 | 점수      |
| --- | ------------------------------------------------------------------------ | --------- | ------- |
| 1 ~ 4 | `Java 17 · Spring Boot · Ollama · PostgreSQL(pgvector) · Gradle` (4회 동일) | 0.65 포함   | **2×4** |


**v1 대비:** Java 17·Gradle 누락 → **전 항목 나열**. DECISIONS chunk 0.73 1순이나 README·ARCHITECTURE가 Context에 함께 들어와 생성 품질 개선.

---

## 5번 — Spring AI (4회)

**질문:** Spring AI를 전면 도입하지 않은 이유?


| 시도  | 한국어 | 내용                            | 점수      |
| --- | --- | ----------------------------- | ------- |
| 1 ~ 3 | ✅   | 직접 구현, RestClient, 선택 사용      | **2×3** |
| 4   | ✅   | 과도하게 짧음 (RestClient·이유 일부 누락) | **1**   |


**합계: 7 / 8.** v1 **중국어 혼입 2/2 → v1.1 0/4.**

---

## 6번 — hit 없을 때 (공식 문장)

**질문:** 검색 hit가 없을 때 앱은 어떻게 동작하나?

### top-k 7 시기 (FAQ + isGrounded 직후, 4회)


| 시도  | retrieval | top FAQ | hits empty → LLM 미호출 | 점수      |
| --- | --------- | ------- | -------------------- | ------- |
| 1 ~ 4 | hit       | ~ 0.70   | 4/4 포함               | **2×4** |


### top-k 10 회귀 (1회)


| retrieval | top FAQ | 답변                                                                           | 점수    |
| --------- | ------- | ---------------------------------------------------------------------------- | ----- |
| hit       | 0.70    | min-score → hits empty → LLM 미호출 → no-answer(`grounded=false`, `sources=[]`) | **2** |


**참고 (평가 제외):** 구어 `hit 없을 때?`는 retrieval hit이나 답이 한 줄 + `dokument` 오타 → **약 1점**. 공식 문장만 v1.1에 기록.

---

## 8번 — 회귀 (top-k 10, 1회)

**질문:** 2025년 매출? (v1: 「이 프로젝트의 2025년 매출은?」)


| 답변                   | 출처  | `grounded` | 점수    |
| -------------------- | --- | ---------- | ----- |
| 문서에서 확인할 수 없는 질문입니다. | 없음  | false      | **2** |


top-k 10·README 확장 후에도 **문서 밖 no-answer 유지** 확인.

---

## 7번 — chunk-size / chunk-overlap (4회, top-k 10)

**질문:** chunk-size와 chunk-overlap은?


| 시도  | retrieval | top 출처 (score)                                 | 답변 요약                                            | 점수      |
| --- | --------- | ---------------------------------------------- | ------------------------------------------------ | ------- |
| 1 ~ 4 | hit       | DECISIONS **0.75**, `__SYSTEM_FAQ.md` **0.71** | 현재 **450 / 150**; 초기 700→450, 100→150; recall 튜닝 | **2×4** |


**합계: 8 / 8.** v1 미실시 → v1.1에서 **4/4 retrieval·동일 답**.

**v1 대비:** FAQ 설정 chunk + DECISIONS·ARCHITECTURE chunk가 Context에 함께 들어와 현재값·변경 이력 모두 정확.

---

## 9 ~ 10번 — 문서 밖 (v1 유지 + 8번 회귀)

v1에서 9·10번은 각 **2점(no-answer)** 으로 측정됨 (`[RAG_EVAL_v1.md` §문항별](RAG_EVAL_v1.md#문항별-v1)). top-k 10·FAQ 추가 이후 **9·10 재측정은 생략**하고, 동일 조건에서 **8번 1회 회귀**로 문서 밖 → no-answer(`grounded=false`, `sources=[]`) 경로가 유지됨을 확인.


| #   | 질문 (v1)           | v1    | v1.1      | 근거                             |
| --- | ----------------- | ----- | --------- | ------------------------------ |
| 9   | Kubernetes로 배포했나? | **2** | **v1 유지** | v1 no-answer; 설정 변경 후 8번 회귀 OK |
| 10  | fine-tuning을 했나?  | **2** | **v1 유지** | v1 no-answer; 설정 변경 후 8번 회귀 OK |


문서 밖 C구간(8 ~ 10) 합계: v1 **6/6** → v1.1에서 **8번 회귀 확인**으로 top-k 10·FAQ 이후에도 no-answer 정책 유지 판정.

---

## v1.1 한 줄 결론

- **6번:** FAQ B안 + `isGrounded()` 수정으로 retrieval **4/4**·정책 설명 **8/8**.
- **1번:** prompt + `temperature: 0` + README + **top-k 10**으로 스택 **8/8**.
- **5번:** 중국어 혼입 해소(**7/8**).
- **7번:** chunk-size/overlap **8/8** (4회 동일).
- **8 ~ 10번:** 8번 회귀 **2점**; 9·10은 **v1 no-answer 유지** (재측정 생략).
- **RAG off:** **0 / 20** — `[RAG_EVAL_v2.md](RAG_EVAL_v2.md)`.

## 프롬프트 2차 개편 후 회귀 (참고, 2026-06)

v1.1 본문 측정 **이후** `PromptBuilder`·`contentPrompt`·`FaqCatalog` 정책 chunk를 정리한 뒤, UI에서 **1·6번만** 재확인 (전체 10문항 재점수 아님).

| # | 질문 | 결과 요약 | 비고 |
| --- | --- | --- | --- |
| 1 | 기술 스택 | 목록만 출력, 마지막 "위와 같습니다" **없음** | v1.1 대비 형식 개선 |
| 6 | hit 없을 때 동작 | LLM 미호출·no-answer 요지 맞음; `grounded`/`sources`/한국어 메시지 **간략** | FAQ chunk 한국어 서술과 정합 |

상세 프롬프트·FAQ SSOT: [`ARCHITECTURE.md`](ARCHITECTURE.md) §8 · `FaqCatalog.java`.

## 후속 구현


| 항목               | 상태                                                                                                 |
| ---------------- | -------------------------------------------------------------------------------------------------- |
| hybrid search 1차 | ✅ `[DECISIONS.md](DECISIONS.md)` §11 — 기본 off; `nomic-embed-text` compare에서 vector/hybrid 순위 차이 확인 |


---

> 측정일: 2026-06 · retrieval/생성 품질 개선 1차
> 설정 SSOT: `application.yml` — `rag.top-k: 10`, `ollama.temperature: 0`

