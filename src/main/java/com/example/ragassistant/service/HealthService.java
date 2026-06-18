package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;
import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.config.RerankerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 의존성(Ollama·DB·TEI reranker) readiness 점검.
 * 각 점검은 짧은 타임아웃으로 격리해, 한 의존성이 느려도 health가 길게 막히지 않게 한다.
 * <p>
 * status 판정:
 * - core(ollama·database) 중 하나라도 DOWN → DOWN (서비스 사실상 응답 불가)
 * - core는 UP인데 reranker가 DOWN(rerank-enabled) → DEGRADED (fallback으로 동작은 함)
 * - 그 외 → UP. rerank-enabled=false면 reranker=DISABLED(상태에 영향 없음)
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DataSource dataSource;
    private final OllamaProperties ollamaProperties;
    private final RerankerProperties rerankerProperties;
    private final RagProperties ragProperties;

    public HealthService(DataSource dataSource, OllamaProperties ollamaProperties,
                         RerankerProperties rerankerProperties, RagProperties ragProperties) {
        this.dataSource = dataSource;
        this.ollamaProperties = ollamaProperties;
        this.rerankerProperties = rerankerProperties;
        this.ragProperties = ragProperties;
    }

    public HealthReport check() {
        String database = checkDatabase();
        String ollama = checkHttp(ollamaProperties.baseUrl(), "/api/tags");
        String reranker = !ragProperties.rerankEnabled()
                ? "DISABLED"
                : checkHttp(rerankerProperties.baseUrl(), "/health");

        boolean coreUp = "UP".equals(database) && "UP".equals(ollama);
        String status;
        if (!coreUp) {
            status = "DOWN";
        } else if ("DOWN".equals(reranker)) {
            status = "DEGRADED";
        } else {
            status = "UP";
        }

        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("ollama", ollama);
        dependencies.put("database", database);
        dependencies.put("reranker", reranker);
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
