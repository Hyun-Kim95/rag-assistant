package com.example.ragassistant.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 요청 스코프 관측 지표 수집기 (ThreadLocal).
 * - RAG 진입점(RagService)이 begin → finally endAndLog 로 감싼다.
 * - 단계 컴포넌트(Retriever·Reranker)는 record* 로 값을 채운다.
 * - 활성 컨텍스트가 없으면 모든 record* 는 no-op → eval runner 등 비웹 호출에서도 안전.
 * <p>
 * 스트리밍은 ChatController가 별도 스레드에서 chatStream을 호출하므로,
 * 그 스레드에서 begin/end가 모두 일어난다(ThreadLocal 정합).
 */
@Component
public class QueryTelemetryContext {

    private static final Logger log = LoggerFactory.getLogger("rag.telemetry");

    private final ThreadLocal<QueryTelemetry> holder = new ThreadLocal<>();

    public void begin(String requestId) {
        holder.set(new QueryTelemetry(requestId));
    }

    public void recordResult(int hitCount, Double topScore, boolean grounded, NoAnswerReason reason) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setResult(hitCount, topScore, grounded, reason);
        }
    }

    public void recordEmbeddingMs(long ms) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setEmbeddingMs(ms);
        }
    }

    public void recordRetrievalMs(long ms) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setRetrievalMs(ms);
        }
    }

    public void recordRerankMs(long ms) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setRerankMs(ms);
        }
    }

    public void recordGenerationMs(long ms) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setGenerationMs(ms);
        }
    }

    public void recordRerankFallback(boolean fallback) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setRerankFallback(fallback);
        }
    }

    public void recordProvider(String name) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setProvider(name);
        }
    }

    public void recordFallbackUsed() {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setFallbackUsed(true);
        }
    }

    public void recordDifficulty(String tier) {
        QueryTelemetry t = holder.get();
        if (t != null) {
            t.setDifficulty(tier);
        }
    }

    /**
     * 현재 요청에서 실제 응답한 provider 이름. 활성 컨텍스트 없으면 null.
     */
    public String currentProvider() {
        QueryTelemetry t = holder.get();
        return t == null ? null : t.provider();
    }

    /**
     * 구조적 로그 한 줄을 남기고 ThreadLocal을 정리한다. 활성 컨텍스트가 없으면 무시.
     * 반드시 finally에서 호출해 ThreadLocal 누수를 막는다.
     */
    public void endAndLog() {
        QueryTelemetry t = holder.get();
        if (t == null) {
            return;
        }
        try {
            log.info("query requestId={} hits={} topScore={} grounded={} noAnswer={} "
                            + "embedMs={} retrieveMs={} rerankMs={} genMs={} totalMs={} rerankFallback={}"
                            + "provider={} fallbackUsed={} difficulty={}",
                    t.requestId(),
                    t.hitCount(),
                    t.topScore() == null ? "-" : String.format("%.4f", t.topScore()),
                    t.grounded(),
                    t.noAnswerReason() == null ? "-" : t.noAnswerReason(),
                    t.embeddingMs(),
                    t.retrievalMs(),
                    t.rerankMs(),
                    t.generationMs(),
                    t.totalMs(),
                    t.rerankFallback() == null ? "-" : t.rerankFallback(),
                    t.provider() == null ? "-" : t.provider(),
                    t.fallbackUsed(),
                    t.difficulty() == null ? "-" : t.difficulty());
        } finally {
            holder.remove();
        }
    }
}
