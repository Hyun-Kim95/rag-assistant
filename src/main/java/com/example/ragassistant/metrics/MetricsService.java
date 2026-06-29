package com.example.ragassistant.metrics;

import com.example.ragassistant.config.MetricsProperties;
import com.example.ragassistant.dto.MetricsSummaryResponse;
import com.example.ragassistant.dto.MetricsSummaryResponse.*;
import com.example.ragassistant.dto.MetricsTimeseriesResponse;
import com.example.ragassistant.repository.CallLogRepository;
import com.example.ragassistant.repository.QueryLogRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채널별 지표 묶음(품질·지연·비용·신뢰 + North Star) 조립.
 * - chat/agent/all: query_logs 기반 품질·지연·비용
 * - voice: call_sessions 기반 신뢰(handoff/완료율) — 토큰·단계지연 없음
 */
@Service
public class MetricsService {

    private final QueryLogRepository queryLogs;
    private final CallLogRepository callLogs;
    private final MetricsProperties props;

    public MetricsService(QueryLogRepository queryLogs, CallLogRepository callLogs, MetricsProperties props) {
        this.queryLogs = queryLogs;
        this.callLogs = callLogs;
        this.props = props;
    }

    public MetricsSummaryResponse summary(LocalDateTime from, LocalDateTime to, String channel, String provider) {
        boolean voiceOnly = "voice".equalsIgnoreCase(channel);
        boolean allChannels = channel == null || "all".equalsIgnoreCase(channel);
        String channelFilter = (voiceOnly || allChannels) ? null : channel; // null = query_logs 전체

        Quality quality = null;
        Latency latency = null;
        Cost cost = null;
        long interactions = 0;

        if (!voiceOnly) {
            Map<String, Object> s = queryLogs.summarize(from, to, channelFilter, provider);
            interactions = asLong(s.get("interactions"));
            quality = new Quality(asDouble(s.get("grounded_rate")), asDouble(s.get("no_answer_rate")));
            latency = new Latency(
                    new Percentiles(asDouble(s.get("p50")), asDouble(s.get("p95")), asDouble(s.get("p99"))),
                    new StageP95(asDouble(s.get("embed_p95")), asDouble(s.get("retrieve_p95")),
                            asDouble(s.get("rerank_p95")), asDouble(s.get("gen_p95"))));

            Double totalCost = asDouble(s.get("total_cost"));
            Double costPer = (interactions > 0 && totalCost != null) ? totalCost / interactions : null;

            Map<String, ProviderCost> byProvider = new LinkedHashMap<>();
            for (Map<String, Object> row : queryLogs.summarizeByProvider(from, to, channelFilter)) {
                long pi = asLong(row.get("interactions"));
                Double pc = asDouble(row.get("total_cost"));
                byProvider.put(String.valueOf(row.get("provider")),
                        new ProviderCost(pi, (pi > 0 && pc != null) ? pc / pi : null));
            }
            cost = new Cost(props.currencyOrDefault(),
                    new Tokens(asDouble(s.get("avg_tokens")), asDouble(s.get("tokens_p95"))),
                    costPer, byProvider);
        }

        // 신뢰 지표(voice)는 채널과 무관하게 항상 제공 → North Star.voice 받침
        Map<String, Object> v = callLogs.summarizeSessions(from, to);
        long sessions = asLong(v.get("sessions"));
        Reliability reliability = new Reliability(asDouble(v.get("handoff_rate")), asDouble(v.get("completion_rate")));

        Map<String, NorthStar> northStar = new LinkedHashMap<>();
        northStar.put("chat", new NorthStar("groundedRate", quality == null ? null : quality.groundedRate()));
        northStar.put("voice", new NorthStar("taskCompletionRate", reliability.taskCompletionRate()));

        return new MetricsSummaryResponse(
                new Range(from.toString(), to.toString()),
                allChannels ? "all" : channel,
                new Counts(interactions, sessions),
                quality, latency, cost, reliability, northStar);
    }

    /**
     * 기간을 bucket 단위로 쪼갠 추이(드리프트). voice 채널은 query_logs 대상이 아니라 빈 추이.
     */
    public MetricsTimeseriesResponse timeseries(LocalDateTime from, LocalDateTime to,
                                                String channel, String provider, String bucket) {
        boolean voiceOnly = "voice".equalsIgnoreCase(channel);
        boolean allChannels = channel == null || "all".equalsIgnoreCase(channel);
        String channelFilter = (voiceOnly || allChannels) ? null : channel;

        List<MetricsTimeseriesResponse.Point> points = new ArrayList<>();
        if (!voiceOnly) {
            for (Map<String, Object> row : queryLogs.timeseries(from, to, channelFilter, provider, bucket)) {
                points.add(new MetricsTimeseriesResponse.Point(
                        asIso(row.get("bucket_start")),
                        asLong(row.get("interactions")),
                        asDouble(row.get("grounded_rate")),
                        asDouble(row.get("no_answer_rate")),
                        asDouble(row.get("p95")),
                        asDouble(row.get("avg_tokens")),
                        asDouble(row.get("avg_top_score")),
                        asDouble(row.get("total_cost"))));
            }
        }
        return new MetricsTimeseriesResponse(
                new MetricsTimeseriesResponse.Range(from.toString(), to.toString()),
                allChannels ? "all" : channel,
                bucket,
                points);
    }

    private static String asIso(Object o) {
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        return o == null ? null : o.toString();
    }

    // DB 집계가 0건일 때 avg/percentile은 NULL → Double null로 전달(빈 상태)
    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
