package com.example.ragassistant.config;

import com.example.ragassistant.parser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, RagProperties.class})
public class AppConfig {

    @Bean
    RestClient ollamaRestClient(OllamaProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
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
