package com.example.ragassistant.config;

import com.example.ragassistant.parser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, RagProperties.class, RerankerProperties.class})
public class AppConfig {

    @Bean
    RestClient ollamaRestClient(OllamaProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    // reranker(TEI) 전용 RestClient — connect/read timeout 필수 (초과 시 Reranker가 fallback)
    @Bean
    RestClient rerankerRestClient(RerankerProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    DocumentParser documentParser() {
        return new DocumentParser();
    }

    // Ollama NDJSON 파싱·SSE JSON 직렬화용
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
