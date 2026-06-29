package com.example.ragassistant.llm;

import com.example.ragassistant.config.OpenAiCompatProperties;
import com.example.ragassistant.exception.LlmResponseException;
import com.example.ragassistant.exception.LlmUnavailableException;
import com.example.ragassistant.observability.QueryTelemetryContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 호환 /v1/chat/completions 클라이언트.
 * base-url 교체만으로 SaaS(Groq 등)·self-hosted(vLLM)를 모두 커버한다.
 * 동기 chat 만 폴백 대상. streamChat 은 폴백 없이 chat() 위임
 */
@Service
public class OpenAiCompatChatClient implements ChatModelClient {

    private final RestClient client;
    private final OpenAiCompatProperties props;
    private final QueryTelemetryContext telemetry;

    public OpenAiCompatChatClient(RestClient openAiCompatRestClient, OpenAiCompatProperties props,
                                  QueryTelemetryContext telemetry) {
        this.client = openAiCompatRestClient;
        this.props = props;
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return props.name();
    }

    // 켜져 있고 api-key가 있으면 라우팅 후보. 실제 도달성 핑은 아님.
    @Override
    public boolean available() {
        return props.enabled() && StringUtils.hasText(props.apiKey());
    }

    @Override
    public String chat(String prompt) {
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", ChatPrompts.SYSTEM),
                        Map.of("role", "user", "content", prompt)),
                "stream", false,
                "temperature", 0);
        try {
            Map<?, ?> res = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.apiKey())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            recordUsage(res);
            return extractContent(res);
        } catch (ResourceAccessException ex) {
            throw new LlmUnavailableException("provider 연결 불가: " + name(), ex);
        } catch (HttpStatusCodeException ex) {
            throw new LlmResponseException(
                    "provider HTTP 오류: " + name() + " status=" + ex.getStatusCode().value(), ex);
        }
    }

    private void recordUsage(Map<?, ?> res) {
        if (telemetry == null || res == null || !(res.get("usage") instanceof Map<?, ?> usage)) {
            return;
        }
        telemetry.recordTokens(asInt(usage.get("prompt_tokens")), asInt(usage.get("completion_tokens")));
    }

    private static Integer asInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    @Override
    public String streamChat(String prompt, Consumer<String> onDelta) {
        // 진짜 스트리밍·폴백 없음. 동기 chat 결과를 1회 delta 로 흘린다.
        String answer = chat(prompt);
        if (StringUtils.hasText(answer)) {
            onDelta.accept(answer);
        }
        return answer;
    }

    /**
     * choices[0].message.content 추출, 단계별 null/형식 방어.
     */
    private String extractContent(Map<?, ?> res) {
        if (res == null) {
            throw new LlmResponseException("provider 응답 body가 null: " + name());
        }
        if (!(res.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmResponseException("provider 응답에 choices가 없음: " + name());
        }
        if (!(choices.get(0) instanceof Map<?, ?> choice)
                || !(choice.get("message") instanceof Map<?, ?> message)
                || message.get("content") == null) {
            throw new LlmResponseException("provider 응답에 message.content가 없음: " + name());
        }
        return message.get("content").toString();
    }
}
