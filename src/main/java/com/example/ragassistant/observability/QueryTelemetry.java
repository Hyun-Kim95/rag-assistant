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
    private String difficulty;              // 난이도 라우팅 시 분류 결과(EASY/HARD), 아니면 null
    private Integer promptTokens;           // provider 미제공 시 null 유지
    private Integer completionTokens;
    private String channel = "chat";        // 기본 chat. agent 경로에서 setChannel("agent")
    private String stopReason;              // agent 전용(FINAL 등), chat은 null

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

    void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * 여러 LLM 호출(난이도 분류 + 본답)을 합산. interaction 전체 토큰을 본다.
     */
    void addTokens(Integer prompt, Integer completion) {
        if (prompt != null) {
            this.promptTokens = (this.promptTokens == null ? 0 : this.promptTokens) + prompt;
        }
        if (completion != null) {
            this.completionTokens = (this.completionTokens == null ? 0 : this.completionTokens) + completion;
        }
    }

    void setChannel(String channel) {
        if (channel != null) {
            this.channel = channel;
        }
    }

    void setStopReason(String stopReason) {
        this.stopReason = stopReason;
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

    String difficulty() {
        return difficulty;
    }

    Integer promptTokens() {
        return promptTokens;
    }

    Integer completionTokens() {
        return completionTokens;
    }

    String channel() {
        return channel;
    }

    String stopReason() {
        return stopReason;
    }
}
