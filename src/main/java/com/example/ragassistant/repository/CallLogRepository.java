package com.example.ragassistant.repository;

import com.example.ragassistant.domain.CallTurn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 통화 세션·턴 로그 영속화. 기존 JdbcTemplate 패턴을 따른다.
 */
@Repository
public class CallLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public CallLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long createSession(LocalDateTime startedAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO call_sessions (started_at) VALUES (?) RETURNING id",
                Long.class,
                Timestamp.valueOf(startedAt));
    }

    public void endSession(Long id, LocalDateTime endedAt, String finalState, String handoffReason) {
        jdbcTemplate.update(
                "UPDATE call_sessions SET ended_at = ?, final_state = ?, handoff_reason = ? WHERE id = ?",
                Timestamp.valueOf(endedAt), finalState, handoffReason, id);
    }

    public void saveTurn(CallTurn turn) {
        jdbcTemplate.update("""
                        INSERT INTO call_turns
                            (session_id, turn_index, user_text_masked, answer_text, grounded,
                             stt_ms, llm_ms, tts_ms, ttfb_ms, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                turn.sessionId(), turn.turnIndex(), turn.userTextMasked(), turn.answerText(), turn.grounded(),
                turn.sttMs(), turn.llmMs(), turn.ttsMs(), turn.ttfbMs(), Timestamp.valueOf(LocalDateTime.now()));
    }
}
