package com.example.ragassistant.repository;

import com.example.ragassistant.observability.QueryLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class QueryLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public QueryLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(QueryLog e, BigDecimal estimatedCost) {
        jdbcTemplate.update("""
                        INSERT INTO query_logs
                            (request_id, channel, provider, grounded, no_answer_reason,
                             hit_count, top_score, embed_ms, retrieve_ms, rerank_ms, gen_ms, total_ms,
                             rerank_fallback, prompt_tokens, completion_tokens, estimated_cost, stop_reason, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                e.requestId(), e.channel(), e.provider(), e.grounded(), e.noAnswerReason(),
                e.hitCount(), e.topScore(), e.embedMs(), e.retrieveMs(), e.rerankMs(), e.genMs(), e.totalMs(),
                e.rerankFallback(), e.promptTokens(), e.completionTokens(), estimatedCost, e.stopReason(),
                Timestamp.valueOf(e.createdAt() == null ? LocalDateTime.now() : e.createdAt()));
    }

    /**
     * 기간 + (선택)channel + (선택)provider 집계.
     * 0건이면 avg/percentile은 NULL을 반환 → 호출부에서 빈 상태로 처리.
     */
    public Map<String, Object> summarize(LocalDateTime from, LocalDateTime to, String channel, String provider) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    count(*)                                                       AS interactions,
                    avg(CASE WHEN grounded THEN 1 ELSE 0 END)                      AS grounded_rate,
                    avg(CASE WHEN no_answer_reason IS NOT NULL THEN 1 ELSE 0 END)  AS no_answer_rate,
                    avg(CASE WHEN stop_reason = 'FINAL' THEN 1 ELSE 0 END)         AS final_rate,
                    percentile_cont(0.5)  WITHIN GROUP (ORDER BY total_ms)         AS p50,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY total_ms)         AS p95,
                    percentile_cont(0.99) WITHIN GROUP (ORDER BY total_ms)         AS p99,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY embed_ms)         AS embed_p95,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY retrieve_ms)      AS retrieve_p95,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY rerank_ms)        AS rerank_p95,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY gen_ms)           AS gen_p95,
                    avg(coalesce(prompt_tokens,0) + coalesce(completion_tokens,0)) AS avg_tokens,
                    percentile_cont(0.95) WITHIN GROUP
                        (ORDER BY coalesce(prompt_tokens,0) + coalesce(completion_tokens,0)) AS tokens_p95,
                    sum(coalesce(estimated_cost,0))                                AS total_cost
                FROM query_logs
                WHERE created_at BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(Timestamp.valueOf(from), Timestamp.valueOf(to)));
        if (channel != null) {
            sql.append(" AND channel = ?");
            args.add(channel);
        }
        if (provider != null) {
            sql.append(" AND provider = ?");
            args.add(provider);
        }
        return jdbcTemplate.queryForMap(sql.toString(), args.toArray());
    }

    /**
     * date_trunc 버킷 단위 화이트리스트(식별자는 바인딩 불가 → SQL 인젝션 차단용).
     */
    private static final java.util.Set<String> ALLOWED_BUCKETS = java.util.Set.of("hour", "day", "week");

    /**
     * 기간을 bucket(hour|day|week) 단위로 쪼갠 시계열 집계(드리프트 추이용).
     * 버킷 안에 행이 없는 구간은 결과에 없음(빈 버킷 미생성 — 호출부에서 연결).
     */
    public List<Map<String, Object>> timeseries(LocalDateTime from, LocalDateTime to,
                                                String channel, String provider, String bucket) {
        String unit = ALLOWED_BUCKETS.contains(bucket) ? bucket : "day";
        StringBuilder sql = new StringBuilder("""
                SELECT
                    date_trunc('%s', created_at)                                   AS bucket_start,
                    count(*)                                                       AS interactions,
                    avg(CASE WHEN grounded THEN 1 ELSE 0 END)                      AS grounded_rate,
                    avg(CASE WHEN no_answer_reason IS NOT NULL THEN 1 ELSE 0 END)  AS no_answer_rate,
                    percentile_cont(0.95) WITHIN GROUP (ORDER BY total_ms)         AS p95,
                    avg(coalesce(prompt_tokens,0) + coalesce(completion_tokens,0)) AS avg_tokens,
                    avg(top_score)                                                 AS avg_top_score,
                    sum(coalesce(estimated_cost,0))                                AS total_cost
                FROM query_logs
                WHERE created_at BETWEEN ? AND ?
                """.formatted(unit));
        List<Object> args = new ArrayList<>(List.of(Timestamp.valueOf(from), Timestamp.valueOf(to)));
        if (channel != null) {
            sql.append(" AND channel = ?");
            args.add(channel);
        }
        if (provider != null) {
            sql.append(" AND provider = ?");
            args.add(provider);
        }
        sql.append(" GROUP BY bucket_start ORDER BY bucket_start");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    /**
     * provider별 건수·비용 분해(인터랙션당 비용 계산용).
     */
    public List<Map<String, Object>> summarizeByProvider(LocalDateTime from, LocalDateTime to, String channel) {
        StringBuilder sql = new StringBuilder("""
                SELECT provider,
                       count(*)                        AS interactions,
                       sum(coalesce(estimated_cost,0)) AS total_cost
                FROM query_logs
                WHERE created_at BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(Timestamp.valueOf(from), Timestamp.valueOf(to)));
        if (channel != null) {
            sql.append(" AND channel = ?");
            args.add(channel);
        }
        sql.append(" GROUP BY provider");
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }
}
