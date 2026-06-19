package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;
import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.config.RerankerProperties;
import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.llm.RoutingChatModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 의존성(chat provider·DB·TEI reranker) readiness 점검.
 * 각 점검은 짧은 타임아웃으로 격리해, 한 의존성이 느려도 health가 길게 막히지 않게 한다.
 * <p>
 * status 판정 (Model Router 반영):
 * - database 가 core. DOWN → DOWN.
 * - chat provider 가 0개 UP → DOWN (chat 불가).
 * - DB·chat 정상인데 reranker DOWN(rerank-enabled) → DEGRADED.
 * - 그 외 → UP.
 * <p>
 * provider 상태: Ollama leg 는 /api/tags 실제 핑(UP/DOWN), OpenAI 호환 leg 는
 * config 수준(available() = enabled+api-key → UP / 아니면 DISABLED). 실제 도달성 핑은 아님.
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DataSource dataSource;
    private final OllamaProperties ollamaProperties;
    private final RerankerProperties rerankerProperties;
    private final RagProperties ragProperties;
    private final List<ChatModelClient> chatProviders;

    public HealthService(DataSource dataSource, OllamaProperties ollamaProperties,
                         RerankerProperties rerankerProperties, RagProperties ragProperties,
                         List<ChatModelClient> chatProviders) {
        this.dataSource = dataSource;
        this.ollamaProperties = ollamaProperties;
        this.rerankerProperties = rerankerProperties;
        this.ragProperties = ragProperties;
        this.chatProviders = chatProviders;
    }

    public HealthReport check() {
        String database = checkDatabase();
        String ollama = checkHttp(ollamaProperties.baseUrl(), "/api/tags");
        String reranker = !ragProperties.rerankEnabled()
                ? "DISABLED"
                : checkHttp(rerankerProperties.baseUrl(), "/health");

        Map<String, String> dependencies = new LinkedHashMap<>();
        int chatUp = 0;
        for (ChatModelClient c : chatProviders) {
            if (c instanceof RoutingChatModelClient) {
                continue; // 라우터 자신은 의존성이 아님
            }
            // Ollama leg 는 실제 /api/tags 핑, 그 외(SaaS)는 config 수준 가용성(D3)
            String providerStatus = (c instanceof OllamaService)
                    ? ollama
                    : (c.available() ? "UP" : "DISABLED");
            dependencies.put(c.name(), providerStatus);
            if ("UP".equals(providerStatus)) {
                chatUp++;
            }
        }
        dependencies.put("database", database);
        dependencies.put("reranker", reranker);

        boolean dbUp = "UP".equals(database);
        String status;
        if (!dbUp || chatUp == 0) {
            status = "DOWN";          // DB 불가 또는 chat provider 0개 UP → 서비스 불가
        } else if ("DOWN".equals(reranker)) {
            status = "DEGRADED";      // 품질 의존성만 다운 → fallback으로 동작
        } else {
            status = "UP";
        }

        return new HealthReport(status, dependencies);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ex) {
            log.warn("DB health 점검 실패: {}", ex.toString());
            return "DOWN";
        }
    }

    /**
     * 2xx면 UP, 그 외(4xx/5xx·연결 실패·타임아웃)는 DOWN.
     */
    private String checkHttp(String baseUrl, String path) {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(1));
            factory.setReadTimeout(Duration.ofSeconds(2));
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build();
            client.get().uri(path).retrieve().toBodilessEntity();
            return "UP";
        } catch (Exception ex) {
            return "DOWN";
        }
    }

    /**
     * 헬스 점검 결과. status: UP | DEGRADED | DOWN.
     */
    public record HealthReport(String status, Map<String, String> dependencies) {
    }
}
