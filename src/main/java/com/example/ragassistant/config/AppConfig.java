package com.example.ragassistant.config;

import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.llm.OllamaChatClient;
import com.example.ragassistant.llm.agent.OllamaAgentClient;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.example.ragassistant.parser.DocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties({OllamaProperties.class, RagProperties.class, RerankerProperties.class,
        RoutingProperties.class, OpenAiCompatProperties.class, AgentProperties.class, VoiceProperties.class,
        MetricsProperties.class})
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

    // 음성 STT(Groq Whisper) 전용 RestClient — multipart 업로드. read timeout은 전사 지연 대비 상향.
    @Bean
    RestClient sttRestClient(VoiceProperties properties) {
        var stt = properties.stt();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(stt.timeoutMs()));
        return RestClient.builder()
                .baseUrl(stt.baseUrl())
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

    // agent 전용 Ollama leg (tool calling). 기존 ollama7bChatClient와 동일 모델·RestClient 재사용.
    @Bean
    OllamaAgentClient ollamaAgentClient(RestClient ollamaRestClient, ObjectMapper objectMapper,
                                        OllamaProperties properties, QueryTelemetryContext telemetry) {
        return new OllamaAgentClient(ollamaRestClient, objectMapper,
                properties.chatModel(), "ollama", properties.temperature(), telemetry);
    }

    // SSE 스트리밍 동안 orchestrator 루프를 요청 스레드와 분리해 실행
    @Bean(destroyMethod = "shutdown")
    ExecutorService agentStreamExecutor() {
        return Executors.newCachedThreadPool();
    }

    // ollama-7b: 기본/강한 leg
    @Bean
    ChatModelClient ollama7bChatClient(RestClient ollamaRestClient, OllamaProperties properties,
                                       ObjectMapper objectMapper, QueryTelemetryContext telemetry) {
        return new OllamaChatClient(ollamaRestClient, objectMapper,
                properties.chatModel(), "ollama-7b", properties.temperature(), telemetry);
    }

    // ollama-1b: 작은/빠른 leg + 난이도 분류기 재사용. 구체 타입 반환 → DifficultyClassifier가 @Qualifier로 주입.
    @Bean
    OllamaChatClient ollama1bChatClient(RestClient ollamaRestClient, OllamaProperties properties,
                                        ObjectMapper objectMapper, QueryTelemetryContext telemetry) {
        return new OllamaChatClient(ollamaRestClient, objectMapper,
                properties.smallChatModel(), "ollama-1b", properties.temperature(), telemetry);
    }
}
