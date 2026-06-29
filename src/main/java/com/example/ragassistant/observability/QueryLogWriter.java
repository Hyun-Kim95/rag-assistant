package com.example.ragassistant.observability;

import com.example.ragassistant.config.MetricsProperties;
import com.example.ragassistant.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * query_logs 적재 + 비용 추정.
 * - 기능 토글이 꺼져 있으면 적재하지 않는다.
 * - 적재 실패는 절대 요청을 막지 않는다 — 통화 로그와 동일 철학.
 */
@Component
public class QueryLogWriter {

    private static final Logger log = LoggerFactory.getLogger(QueryLogWriter.class);

    private final QueryLogRepository repository;
    private final MetricsProperties props;

    public QueryLogWriter(QueryLogRepository repository, MetricsProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public void write(QueryLog entry) {
        if (!props.enabled()) {
            return;
        }
        try {
            repository.insert(entry, estimateCost(entry));
        } catch (Exception ex) {
            log.warn("query_logs 적재 실패(무시): requestId={} cause={}", entry.requestId(), ex.toString());
        }
    }

    /**
     * 토큰 × provider 단가 → 추정 비용. 단가 미설정 또는 토큰 0이면 null.
     */
    private BigDecimal estimateCost(QueryLog e) {
        MetricsProperties.ProviderRate rate = props.rateFor(e.provider());
        if (rate == null) {
            return null;
        }
        int prompt = e.promptTokens() == null ? 0 : e.promptTokens();
        int completion = e.completionTokens() == null ? 0 : e.completionTokens();
        if (prompt == 0 && completion == 0) {
            return null;
        }
        double cost = prompt / 1000.0 * rate.promptPer1k()
                + completion / 1000.0 * rate.completionPer1k();
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }
}
