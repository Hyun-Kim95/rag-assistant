package com.example.ragassistant.observability;

/**
 * RAG 요청 1건의 관측 지표.
 * 요청 처리 중 각 단계가 값을 채우고, 끝에서 한 줄 구조적 로그로 출력한다.
 * 질문 원문·답변은 담지 않는다(지표만 → PII 회피).
 */
public class QueryTelemetry {

    private final String requestId;
    private final long startNanos = System.nanoTime();

    private int hitCount;
    private Double topScore;                // null = 후보 없음
    private boolean grounded;
    private NoAnswerReason noAnswerReason;  // null = 정상 답변
    private long embeddingMs;
    private long retrievalMs;
    private long rerankMs;
    private long generationMs;
    private Boolean rerankFallback;         // null = rerank 미수행(off/후보 없음)
    private String provider;                // 실제 답변한 chat provider 이름 (null = LLM 미호출)
    private boolean fallbackUsed;           // 폴백으로 다른 leg가 응답했는지

    QueryTelemetry(String requestId) {
        this.requestId = requestId;
    }

    long totalMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }   // 나노초 차이 → 밀리초

    String requestId() {
        return requestId;
    }

    void setResult(int hitCount, Double topScore, boolean grounded, NoAnswerReason noAnswerReason) {
        this.hitCount = hitCount;
        this.topScore = topScore;
        this.grounded = grounded;
        this.noAnswerReason = noAnswerReason;
    }

    void setEmbeddingMs(long ms) {
        this.embeddingMs = ms;
    }

    void setRetrievalMs(long ms) {
        this.retrievalMs = ms;
    }

    void setRerankMs(long ms) {
        this.rerankMs = ms;
    }

    void setGenerationMs(long ms) {
        this.generationMs = ms;
    }

    void setRerankFallback(boolean fallback) {
        this.rerankFallback = fallback;
    }

    void setProvider(String provider) {
        this.provider = provider;
    }

    void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }

    int hitCount() {
        return hitCount;
    }

    Double topScore() {
        return topScore;
    }

    boolean grounded() {
        return grounded;
    }

    NoAnswerReason noAnswerReason() {
        return noAnswerReason;
    }

    long embeddingMs() {
        return embeddingMs;
    }

    long retrievalMs() {
        return retrievalMs;
    }

    long rerankMs() {
        return rerankMs;
    }

    long generationMs() {
        return generationMs;
    }

    Boolean rerankFallback() {
        return rerankFallback;
    }

    String provider() {
        return provider;
    }

    boolean fallbackUsed() {
        return fallbackUsed;
    }
}
