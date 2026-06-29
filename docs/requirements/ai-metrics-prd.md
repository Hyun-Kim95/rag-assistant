# AI 프로덕트 지표 관측 (Metrics & Observability) PRD

> 상태: **초안(Draft)** — 사용자 승인 전. 본 PRD 승인 전에는 구현 코드를 작성하지 않는다(Gate 1).
> 대상: RAG Assistant(Spring Boot) — `/api/chat`·`/api/agent`·Voice(`/ws/voice`) 채널의 품질·비용·신뢰 지표를 종합 관측한다.
> 관련 문서: [`ARCHITECTURE.md`](../ARCHITECTURE.md) §11(RAG 평가)·§12(관측성) · [`DECISIONS.md`](../DECISIONS.md)

## 1. 전제·가정 (product-monetization-default)

| 항목 | 기본값 |
|---|---|
| 사업자 | 없음 (개인사업자·법인 미보유) |
| 수익 | 광고·후원 수준만. 구독·상품 판매·B2B 매출 미포함 |
| 결제·정산 | PG·세금계산서·매출 신고를 설계에 넣지 않음 |

- 본 PRD의 **토큰 비용(Token Cost)** 은 사용자 과금/청구가 아니라 **운영 비용 관측(operational cost)** 이다. 결제·청구·정산 흐름은 범위에서 제외한다.
- 본 PRD는 기존 운영 제품에 대한 **부분 기능 확장(brownfield)** 이다. 고객 E2E(이중 목업·디자인 선택) 흐름은 적용하지 않는다.

## 2. 배경 / 문제 정의

AI 프로덕트 측정은 "한두 개의 숫자"가 아니라 "여러 신호를 종합한 판단"으로 바뀌고 있다. 이 레포는 신호의 **원천 데이터**를 이미 상당수 보유하지만(아래), 이를 **비율·분위수로 집계해 한곳에서 보는 레이어**가 없다.

| 지표 범주 | 블로그 지표 | 현재 보유 | 격차 |
|---|---|---|---|
| 모델 품질 | 근거 충실도(Groundedness) | `grounded` 플래그(chat/agent/voice 전반) | 비율 집계 없음 |
| 모델 품질 | 환각률(Hallucination) | eval `mustNotContain` 위반 채점 | 온라인 비율 없음 |
| 모델 품질 | 사실 일치도·답변 적합도 | eval 룰 채점(0/1/2) | RAGAS식 연속 점수 없음 |
| 운영·비용 | 응답 속도 P95 | 단계별 ms 로그(embed/retrieve/rerank/gen), voice `*_ms` | **P50(avg)만**, P95/P99 없음 |
| 운영·비용 | 인터랙션당 토큰 비용 | — | **수집 자체 없음** |
| 신뢰·안전 | 사용자 개입률 | Voice handoff(`NO_ANSWER_X2`) | 비율 집계 없음 |
| 신뢰·안전 | 작업 완료율 | voice `final_state`, agent `stopReason` | 비율 집계 없음 |
| 신뢰·안전 | 모델 드리프트 | eval 실행 이력(스냅샷) | 시계열 추적 없음 |

> 참고: `ARCHITECTURE.md` §3 "아직 없음"에 `질의 로그 DB 저장(chat_logs)`·`메트릭 수집기(Prometheus)`가 명시돼 있다. 본 PRD는 그중 **질의 로그 영속화 + 집계 API**를 대상으로 하고, 외부 수집기(Prometheus 등)는 제외한다.

## 3. 목표 / 비목표

### 목표
- 채널별(chat·agent·voice) **품질·비용·신뢰 지표를 단일 집계 API로 조회**한다.
- 지연을 **P50/P95/P99 분위수**로 측정한다(최악 경험 가시화).
- LLM 호출의 **토큰 사용량을 수집**하고 provider 단가로 **인터랙션당 비용을 추정**한다.
- 채널별 **North Star + 받침 지표**를 정의하고 집계로 노출한다.

### 비목표 (이번 범위 아님)
- 외부 메트릭 수집기/시계열 DB(Prometheus, Grafana, OTel) 연동.
- 실시간 알림(Alerting)·SLO 위반 통지.
- 사용자 과금·청구·정산.
- LLM-as-judge 기반 정성 평가 자동화(룰 기반 eval은 유지).

## 4. 사용자 / 이해관계자

| 유형 | 니즈 | 사용 화면/경로 |
|---|---|---|
| 운영자/개발자(주) | 품질·지연·비용 추세, 병목·degraded 식별 | `GET /api/metrics/*`, (선택) 대시보드 |
| AI PM | North Star·받침 지표로 피처 방향 판단 | 집계 요약·리포트 |
| QA | 회귀·드리프트 점검 | eval 리포트 + 집계 비교 |

## 5. 범위

### 5.1 핵심 (Must)
1. **질의 로그 영속화**: chat/agent 요청 1건의 텔레메트리(`QueryTelemetry` 필드 + 토큰)를 DB에 적재. Voice는 기존 `call_turns` 재사용.
2. **토큰 수집**: provider 응답에서 prompt/completion 토큰 추출(Ollama `prompt_eval_count`/`eval_count`, OpenAI-compat `usage`). 미제공 시 `null`.
3. **비용 추정**: provider별 단가(config) × 토큰 → 인터랙션당 비용. 로컬(Ollama)은 단가 0(=금전 비용 0) 허용.
4. **집계 API**: 기간·채널 필터로 분위수(P50/P95/P99)·비율·비용 합계를 반환하는 `GET /api/metrics/summary`.
5. **North Star 집계**: 채널별 핵심 지표 1개 + 받침 지표(아래 §6.4)를 응답에 포함.

### 5.2 선택 (Should/Could)
- (S) **메트릭 대시보드 UI**: 기존 `static/`에 읽기 전용 페이지(`metrics.html`)로 요약 카드·표 노출.
- (C) **드리프트 뷰**: eval 리포트 이력(`runs/`)을 시계열로 비교하는 집계.
- (C) **RAGAS 연동**: 오프라인 평가에 faithfulness/answer-relevancy/context-precision 추가(별도 파이프라인, eval 확장).

### 5.3 제외 (Out of scope)
- Prometheus/OTel/외부 APM, 실시간 알림, 과금/청구, LLM-as-judge.

## 6. 지표 정의

### 6.1 모델 품질
| 지표 | 정의 | 계산 |
|---|---|---|
| `groundedRate` | 근거 기반 응답 비율 | `grounded=true` 건수 / 전체 |
| `noAnswerRate` | no-answer 비율 | `noAnswerReason != null` / 전체 |
| `hallucinationSignal` | (오프라인) eval `mustNotContain` 위반율 | 위반 문항 / 전체 문항 (eval 리포트 출처) |

> 온라인(운영) 환각률은 정답 라벨이 없어 직접 산출 불가. 운영에서는 `groundedRate`·`noAnswerRate`를 환각 **대리지표(proxy)** 로 쓰고, 정밀 환각률은 eval 세트에서 본다.

### 6.2 운영·비용
| 지표 | 정의 | 계산 |
|---|---|---|
| `latencyP50/P95/P99` | 총 지연 분위수(ms) | 적재된 `totalMs` 분포의 분위수 |
| `stageLatency` | 단계별(embed/retrieve/rerank/gen) 분위수 | 단계 ms 분포 |
| `tokensPerInteraction` | 인터랙션당 토큰(prompt+completion) | 평균·P95 |
| `costPerInteraction` | 인터랙션당 추정 비용 | Σ(토큰×단가) / 건수, provider별 분해 |

> 분위수는 **평균 금지** 원칙에 맞춰 P95를 1차 기준으로 노출(P50·P99 병기).

### 6.3 신뢰·안전
| 지표 | 정의 | 계산 |
|---|---|---|
| `handoffRate` | 상담원 전환 비율(voice) | `final_state=HANDOFF` / 전체 세션 |
| `taskCompletionRate` | 작업 완료 비율 | voice `COMPLETED`/전체, agent `stopReason=FINAL`/전체 |
| `overrideProxy` | 사용자 개입 대리지표 | handoffRate(현 구조상 명시적 "사용자 수정" 신호 없음 → 대리) |
| `drift` (선택) | eval 점수 시계열 변화 | 최근 N개 eval run 점수 추세 |

### 6.4 North Star (채널별 묶음)
| 채널 | North Star | 받침 지표 |
|---|---|---|
| Voice 콜봇 | **해결률 = `taskCompletionRate`(=1−handoffRate 근사)** | `groundedRate`, `latencyP95(ttfb)` |
| Chat/Agent/MCP | **`groundedRate`(근거율)** | `latencyP95`, `costPerInteraction` |

## 7. 데이터 모델 (초안)

> 기존 voice `call_sessions`/`call_turns`(`schema.sql` 자동 생성)와 동일 운영 방식(`CREATE TABLE IF NOT EXISTS`)을 따른다. documents/chunks/embeddings 수동 DDL 정책은 건드리지 않는다.

### `query_logs` (신규, chat/agent 1건 = 1행)
| column | type | note |
|---|---|---|
| id | bigserial PK | |
| request_id | varchar | `X-Request-Id`와 동일 상관키 |
| channel | varchar(10) | `chat` / `agent` |
| provider | varchar(40) null | 실제 응답 provider |
| grounded | boolean | |
| no_answer_reason | varchar(20) null | `EMPTY_HITS`/`LLM_NO_ANSWER`/null |
| hit_count | int | |
| top_score | double null | rerank on이면 rerank score |
| embed_ms / retrieve_ms / rerank_ms / gen_ms / total_ms | int | 단계·총 지연 |
| rerank_fallback | boolean null | |
| prompt_tokens / completion_tokens | int null | provider 미제공 시 null |
| estimated_cost | numeric(12,6) null | 토큰×단가(USD 등 단위 config) |
| stop_reason | varchar(20) null | agent 전용(`FINAL`/`MAX_STEPS`/…) |
| created_at | timestamp | |

- **질문 원문·답변 본문은 저장하지 않는다**(기존 텔레메트리 PII 회피 원칙 유지). 지표·메타만.
- Voice 지표는 기존 `call_turns`/`call_sessions`에서 집계(중복 적재 없음). 토큰·비용 컬럼은 필요 시 `call_turns`에 후속 확장(미확정 §10).

### 단가 설정 (`application.yml`)
```yaml
metrics:
  enabled: true
  cost:
    currency: USD
    providers:
      groq:    { prompt-per-1k: 0.00005, completion-per-1k: 0.00008 }   # 예시값(확정 필요)
      ollama:  { prompt-per-1k: 0.0,     completion-per-1k: 0.0 }       # 로컬 = 금전비용 0
```

## 8. API 계약 (초안)

### `GET /api/metrics/summary`
쿼리: `from`(ISO-8601, 선택)·`to`(선택)·`channel`(`chat|agent|voice|all`, 기본 `all`)·`provider`(선택).

응답 200:
```json
{
  "range": { "from": "2026-06-01T00:00:00Z", "to": "2026-06-29T00:00:00Z" },
  "channel": "all",
  "counts": { "interactions": 1240, "sessions": 86 },
  "quality": { "groundedRate": 0.91, "noAnswerRate": 0.07 },
  "latencyMs": {
    "total": { "p50": 820, "p95": 4300, "p99": 9100 },
    "stage": { "embedP95": 60, "retrieveP95": 210, "rerankP95": 180, "genP95": 3900 }
  },
  "cost": {
    "currency": "USD",
    "tokensPerInteraction": { "avg": 1830, "p95": 4200 },
    "costPerInteraction": 0.00021,
    "byProvider": { "groq": { "interactions": 540, "costPerInteraction": 0.00038 },
                    "ollama": { "interactions": 700, "costPerInteraction": 0.0 } }
  },
  "reliability": { "handoffRate": 0.12, "taskCompletionRate": 0.88 },
  "northStar": { "chat": { "metric": "groundedRate", "value": 0.91 },
                 "voice": { "metric": "taskCompletionRate", "value": 0.88 } }
}
```

- 데이터 없음(기간 내 0건): 200 + 카운트 0·지표 `null`(빈 상태). 오류 아님.
- `metrics.enabled=false`: `404`(기능 비활성) 또는 요약에서 제외 — §10 미확정.
- 인증: 기존 API와 동일 정책(현재 비인증 로컬). 운영 노출 시 권한 가드 필요 → §10 미확정.

### 적재 경로(쓰기, 내부)
- 신규 엔드포인트 없음. `QueryTelemetryContext`가 요청 종료 시 로그 한 줄을 남기는 지점에 **DB 적재 1회**를 추가(비동기·예외 흡수, 통화 로그와 동일 "저장 실패는 요청을 막지 않음").

## 9. 화면 (선택 범위)

선택 채택 시 `static/metrics.html` 읽기 전용 1페이지:
- 요약 카드(North Star·P95·costPerInteraction·groundedRate)
- 채널 토글(chat/agent/voice/all), 기간 선택
- 상태 처리: 기본 / 로딩 / **빈 데이터(기간 내 0건)** / 오류 / (운영 시)권한 제한
- 다크모드: 기존 `static/` 테마 규칙을 따른다(있을 때).

## 10. 미확정 항목 (구현 착수 전 해소)

| ID | 항목 | 옵션 | 추천 |
|---|---|---|---|
| U1 | 지표 영속화 위치 | (a) 신규 `query_logs` 테이블 (b) 로그 파싱 (c) 인메모리 윈도우 | **(a)** — voice와 일관, 재현·집계 용이 |
| U2 | 분위수 계산 위치 | (a) SQL `percentile_cont` (b) 앱 메모리 | **(a)** — Postgres 네이티브, 대량 OK |
| U3 | provider 단가 출처/단위 | config 고정값 / 통화 단위 | **확정**: `application.yml` config + `USD`. groq=`llama-3.1-8b-instant` $0.05/1M in·$0.08/1M out, ollama=0 |
| U4 | `metrics.enabled=false` 시 동작 | 404 / 빈 응답 | 404(기능 토글 명확) |
| U5 | 운영 노출 시 인증·권한 | 로컬 비인증 유지 / 토큰 가드 | 로컬 PoC는 비인증, 운영 노출은 별도 합의 |
| U6 | 토큰 비용 컬럼을 voice에도 추가할지 | 이번 포함 / 후속 | 후속(이번엔 chat/agent 우선) |
| U7 | 선택 범위(대시보드·RAGAS·드리프트) 채택 여부 | 채택/보류 | 1차는 핵심만, 선택은 후속 |

> U1·U2는 되돌리기 비용이 있는 계약/모델 선택이므로 **승인 시 함께 확정**한다.

## 11. 수용 기준 (Acceptance Criteria, ATDD-lite)

행위·계약·상태 중심. AC ID는 테스트와 1:1 추적한다.

- **AC-01** (적재·계약): `grounded=true`인 `/api/chat` 1건 처리 후, `query_logs`에 `channel=chat`·`grounded=true`·`total_ms>0`·`request_id`(헤더 `X-Request-Id`와 동일) 1행이 적재된다.
- **AC-02** (no-answer 상태): 검색 hit 0 질의는 `noAnswerReason` 비-null로 적재되고, 요약의 `noAnswerRate`에 반영된다.
- **AC-03** (토큰): provider가 usage를 제공하면 `prompt_tokens`/`completion_tokens`가 양수로 적재되고, 미제공 시 `null`(요청은 정상 처리).
- **AC-04** (비용): provider 단가 설정 시 `estimated_cost ≈ Σ(tokens/1k × 단가)`(허용오차 내). Ollama 단가 0이면 비용 0.
- **AC-05** (분위수): `GET /api/metrics/summary`가 `latencyMs.total.p50 ≤ p95 ≤ p99`를 만족하는 값을 반환한다.
- **AC-06** (빈 상태): 기간 내 0건이면 200 + `counts.interactions=0` + 지표 `null`(오류 아님).
- **AC-07** (채널 필터): `channel=voice`는 `call_sessions`/`call_turns` 기반 `handoffRate`·`taskCompletionRate`를 반환한다.
- **AC-08** (North Star): 응답 `northStar.chat.metric="groundedRate"`, `northStar.voice.metric="taskCompletionRate"`.
- **AC-09** (장애 격리): 지표 DB 적재가 실패해도 `/api/chat`·`/api/agent` 응답은 정상 반환된다(예외 흡수, 통화 로그와 동일).
- **AC-10** (토글): `metrics.enabled=false`면 적재를 하지 않고 `GET /api/metrics/summary`는 U4에서 정한 동작(404 권장)을 한다.
- **AC-11** (상태코드): 잘못된 기간 파라미터(`from>to`)는 `400 BAD_REQUEST`(기존 `ErrorResponse` 형식).

> 핵심 AC(01·03·05·06·09)는 ATDD-lite RED로 먼저 둔다. Voice(AC-07)는 기존 데이터 재사용이라 통합 테스트 중심.

## 12. 정책 / 예외 / 상태 처리

| 상황 | 동작 |
|---|---|
| 지표 적재 실패(DB) | 예외 흡수, 원 요청 정상(로그만 경고) |
| provider usage 누락 | 토큰/비용 `null`, 나머지 지표 정상 |
| 기간 0건 | 빈 상태 200 |
| 잘못된 파라미터 | 400 `BAD_REQUEST` |
| 기능 비활성 | 404(U4) |
| 운영 권한(미정) | U5 — 로컬 비인증, 운영 노출 시 가드 |

## 13. 구현 분담 · 트랙 · Gate 점검

### 역할(에이전트)
- **backend-agent (주)**: `query_logs` 스키마, 텔레메트리 적재, 토큰 추출/비용 계산, 집계 쿼리·`GET /api/metrics/summary`. 이유: DB·서비스 로직·API 계약이 핵심.
- **frontend-agent (선택 범위 시)**: `static/metrics.html` 요약 대시보드(읽기 전용·상태 UI·다크모드).
- **qa-agent**: AC↔테스트 매핑, 회귀(통화 로그·기존 chat 경로 무영향) 검증.
- **docs-agent**: 완료 후 `ARCHITECTURE.md` §11/§12·`DECISIONS.md` 신규 항목 반영.

### 트랙 (병렬 가능 여부)
| 트랙 | 내용 | 의존성 | 병렬 |
|---|---|---|---|
| T1 (backend) | 스키마 + 적재 + 토큰/비용 | 없음(선행) | 1차 단독 |
| T2 (backend) | 집계 쿼리 + `/api/metrics/summary` | T1 스키마 확정 후 | T1 일부와 직렬 |
| T3 (frontend, 선택) | `metrics.html` | **T2 API 계약 확정 후** 병렬 | Gate 2 충족 시 |

- 파일 충돌 주의: T1·T2는 모두 `observability`/`config`/신규 `metrics` 패키지·`schema.sql`을 건드리므로 **순차(T1→T2)** 권장. T3는 별도 `static/` 영역이라 T2 계약 확정 후 충돌 거의 없음.
- 통합 책임(Integration Owner): backend-agent. `schema.sql`·`application.yml`·집계 계약의 단일 정합 관리.

### Gate 점검
- **Gate 1**: 본 PRD(목표·흐름·범위·정책·미확정) + AC(§11) + API 계약 초안(§8) + (선택)화면 스펙(§9) → **승인 시 충족**. 단 U1·U2·U3 확정 필요.
- **Gate 2 (T3 병렬 조건)**: `/api/metrics/summary` 계약 확정 + 빈/오류/권한 상태 정의 + (UI 채택 시)디자인 승인 후에만 frontend 병렬. 충족 전엔 backend(T1·T2)만 진행.
- **ATDD-lite**: Gate 2 충족 후·구현 전 AC-01/03/05/06/09 RED 스켈레톤 작성.

## 14. 마일스톤(제안)
1. **M1 (핵심)**: T1+T2 — 적재·토큰·비용·집계 API + AC RED→GREEN. **✅ 완료(2026-06-29)** — chat/agent/voice 3채널 적재·집계 검증.
2. **M2 (선택)**: T3 대시보드. **✅ 완료(2026-06-29)** — `static/metrics.html` 읽기 전용 대시보드(필터·상태 처리), 브라우저 렌더 검증.
3. **M3 (선택)**: 드리프트 뷰 / RAGAS 연동.
   - **드리프트 뷰 ✅ 완료(2026-06-29)** — `GET /api/metrics/timeseries`(bucket=hour|day|week) + 대시보드 추이 섹션(인라인 SVG 라인차트). `query_logs` 시간축 집계 재사용.
   - **RAGAS 연동 (미착수)** — 배치 품질 평가(LLM-as-judge). Python 스택 도입 여부 등 별도 결정 필요.

## 15. 열린 질문 (사용자 확인 요청)
1. 1차 범위를 **핵심(M1)만**으로 좁힐까, 대시보드(M2)까지 포함할까?
2. 비용 단가(U3)는 어떤 provider·통화 기준으로 확정할까(예: Groq 실단가, USD)?
3. 운영 노출 인증(U5)이 이번 PoC 범위에 필요한가, 로컬 비인증으로 둘까?

## 16. 결정 로그 (2026-06-29 확정)

| 질문 | 결정 |
|---|---|
| 1차 범위 | **핵심 M1만** (T1 적재·토큰·비용 + T2 집계 API). 대시보드(M2)·드리프트·RAGAS(M3)는 후속 |
| U1 영속화 | **신규 `query_logs` 테이블** 채택 (voice `call_*`와 동일 `schema.sql` 운영) |
| U2 분위수 | **Postgres `percentile_cont`** 채택 |
| U5 인증 | **로컬 비인증 유지** (운영 노출은 별도 합의) |
| U3 단가 | provider 단가는 `application.yml` config. 실단가 확정: groq `llama-3.1-8b-instant` $0.05/1M in·$0.08/1M out(=0.00005/0.00008 per 1K, groq.com/pricing 2026-06), ollama=0 |
| U6 voice 토큰 | 이번 미포함(후속). voice는 기존 `call_*` 집계만 |

**M1 채널 적용 범위:** `chat`(sync+stream)은 기존 `QueryTelemetryContext` 경로로 완전 적재. **`agent` 채널은 현재 텔레메트리 미연결**이라, M1에서 별도 와이어링이 필요(본 PRD에선 chat 우선 적재 + agent는 동일 패턴 확장으로 명시). `voice`는 기존 `call_sessions`/`call_turns` 집계 재사용.
