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

CREATE INDEX IF NOT EXISTS idx_call_turns_session ON call_turns (session_id);
