package com.example.ragassistant.config;

import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.llm.OllamaChatClient;
import com.example.ragassistant.parser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, RagProperties.class, RerankerProperties.class,
        RoutingProperties.class, OpenAiCompatProperties.class})
public class AppConfig {

    @Bean
    RestClient ollamaRestClient(OllamaProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        // 무한 생성/응답 지연 backstop. 기본 120s, ollama.timeout-ms로 상향 가능(느린 로컬 모델 콜드 로드 대비)
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMsOrDefault()));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
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

    // OpenAI 호환(Groq 등) 전용 RestClient — connect/read timeout 필수(폴백 트리거 조건)
    @Bean
    RestClient openAiCompatRestClient(OpenAiCompatProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs() > 0 ? properties.timeoutMs() : 8000));
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    // ollama-7b: 기본/강한 leg
    @Bean
    ChatModelClient ollama7bChatClient(RestClient ollamaRestClient, OllamaProperties properties,
                                       ObjectMapper objectMapper) {
        return new OllamaChatClient(ollamaRestClient, objectMapper,
                properties.chatModel(), "ollama-7b", properties.temperature());
    }

    // ollama-1b: 작은/빠른 leg + 난이도 분류기 재사용. 구체 타입 반환 → DifficultyClassifier가 @Qualifier로 주입.
    @Bean
    OllamaChatClient ollama1bChatClient(RestClient ollamaRestClient, OllamaProperties properties,
                                        ObjectMapper objectMapper) {
        return new OllamaChatClient(ollamaRestClient, objectMapper,
                properties.smallChatModel(), "ollama-1b", properties.temperature());
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
