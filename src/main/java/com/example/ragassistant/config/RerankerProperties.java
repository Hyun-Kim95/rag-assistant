package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.reranker")
public record RerankerProperties(
        String baseUrl,   // TEI 컨테이너 (예: http://localhost:8085)
        String model,     // 기록·로그용 (요청 바디엔 불필요 — 모델은 컨테이너 --model-id로 고정)
        int timeoutMs     // 추론 지연 대비 read timeout. 초과 시 예외 → Reranker가 fallback
) {
}
