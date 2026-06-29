-- Voice PoC 통화 로그. 기존 정책상 repo migration 미사용 → 앱 시작 시 IF NOT EXISTS로 보장.
-- documents/document_chunks/document_embeddings 등 기존 테이블은 별도 수동 DDL(ARCHITECTURE.md) 유지.

CREATE TABLE IF NOT EXISTS call_sessions (
    id             BIGSERIAL PRIMARY KEY,
    started_at     TIMESTAMP   NOT NULL,
    ended_at       TIMESTAMP,
    final_state    VARCHAR(20),             -- COMPLETED / HANDOFF / ERROR
    handoff_reason VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS call_turns (
    id               BIGSERIAL PRIMARY KEY,
    session_id       BIGINT  NOT NULL REFERENCES call_sessions(id) ON DELETE CASCADE,
    turn_index       INT     NOT NULL,
    user_text_masked TEXT,                  -- PII 마스킹 후 저장
    answer_text      TEXT,
    grounded         BOOLEAN NOT NULL DEFAULT FALSE,
    stt_ms           INT,                   -- 브라우저 STT 발화~final (클라 측정)
    llm_ms           INT,                   -- RAG+LLM 스트림 소요
    tts_ms           INT,                   -- TTS 합성 소요
    ttfb_ms          INT,                   -- 발화 처리 시작~첫 answer.delta
    created_at       TIMESTAMP NOT NULL
);

-- AI 지표 관측: chat/agent 인터랙션 1건 = 1행. 질문 원문·답변 본문은 저장하지 않는다(PII 회피, 지표만).
CREATE TABLE IF NOT EXISTS query_logs (
    id                BIGSERIAL PRIMARY KEY,
    request_id        VARCHAR(64),              -- X-Request-Id 상관키
    channel           VARCHAR(10)  NOT NULL DEFAULT 'chat',  -- chat / agent
    provider          VARCHAR(40),              -- 실제 응답 provider (ollama-7b/groq 등)
    grounded          BOOLEAN      NOT NULL DEFAULT FALSE,
    no_answer_reason  VARCHAR(20),              -- EMPTY_HITS / LLM_NO_ANSWER / null
    hit_count         INT          NOT NULL DEFAULT 0,
    top_score         DOUBLE PRECISION,         -- rerank on이면 rerank score
    embed_ms          INT,
    retrieve_ms       INT,
    rerank_ms         INT,
    gen_ms            INT,
    total_ms          INT,
    rerank_fallback   BOOLEAN,                  -- TEI 실패 fallback 여부 (null=미수행)
    prompt_tokens     INT,                      -- provider 미제공 시 null
    completion_tokens INT,
    estimated_cost    NUMERIC(12,6),            -- 토큰×단가 (단가 미설정/토큰없음 시 null)
    stop_reason       VARCHAR(20),              -- agent 전용(FINAL 등), chat은 null
    created_at        TIMESTAMP    NOT NULL
);

-- 집계는 기간·채널로 자주 필터 → 인덱스로 percentile/range 스캔 비용 절감
CREATE INDEX IF NOT EXISTS idx_query_logs_created_at ON query_logs (created_at);
CREATE INDEX IF NOT EXISTS idx_query_logs_channel    ON query_logs (channel);
CREATE INDEX IF NOT EXISTS idx_call_turns_session ON call_turns (session_id);
