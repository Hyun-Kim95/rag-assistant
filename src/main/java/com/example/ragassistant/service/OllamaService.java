package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaService {

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    public OllamaService(RestClient ollamaRestClient, OllamaProperties properties) {
        this.ollamaRestClient = ollamaRestClient;
        this.properties = properties;
    }

    public String chat(String prompt) {
        Map<String, Object> request = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false
        );

        Map<?, ?> response = ollamaRestClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("message") == null) {
            throw new IllegalStateException("Empty response from Ollama chat API");
        }

        Object message = response.get("message");
        if (message instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
            return messageMap.get("content").toString();
        }

        throw new IllegalStateException("Unexpected Ollama chat response format");
    }

    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        Map<String, Object> request = Map.of(
                "model", properties.embeddingModel(),
                "input", text
        );

        Map<String, Object> response = ollamaRestClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("embeddings") == null) {
            throw new IllegalStateException("Empty response from Ollama embed API");
        }

        List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("No embeddings returned from Ollama");
        }

        return embeddings.get(0);
    }
}
