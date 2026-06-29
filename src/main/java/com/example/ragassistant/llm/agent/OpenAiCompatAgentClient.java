package com.example.ragassistant.llm.agent;

import com.example.ragassistant.config.OpenAiCompatProperties;
import com.example.ragassistant.exception.LlmResponseException;
import com.example.ragassistant.exception.LlmUnavailableException;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환(Groq 등) tool calling 클라이언트.
 * /v1/chat/completions 에 tools 를 실어 보내고 choices[0].message.tool_calls 를 파싱한다.
 * - OpenAI 규약: tool_calls[].function.arguments 는 JSON '문자열' → ToolCall.argumentsJson에 그대로 저장.
 * - 실패는 기존 LLM 예외로 던져 RoutingAgentChatClient 폴백·GlobalExceptionHandler와 호환.
 */
@Service
public class OpenAiCompatAgentClient implements AgentChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatAgentClient.class);

    private final RestClient client;
    private final OpenAiCompatProperties props;
    private final ObjectMapper objectMapper;
    private final QueryTelemetryContext telemetry;

    public OpenAiCompatAgentClient(RestClient openAiCompatRestClient, OpenAiCompatProperties props,
                                   ObjectMapper objectMapper, QueryTelemetryContext telemetry) {
        this.client = openAiCompatRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return props.name();
    }

    @Override
    public boolean available() {
        return props.enabled() && StringUtils.hasText(props.apiKey());
    }

    @Override
    public AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("messages", toWireMessages(messages));
        List<Map<String, Object>> wireTools = toWireTools(tools);
        if (!wireTools.isEmpty()) {         // tools 비면 tool_choice도 빼야 함(빈 배열 거부 provider 대비)
            body.put("tools", wireTools);
            body.put("tool_choice", "auto");
        }
        body.put("temperature", 0);
        body.put("stream", false);
        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode res = readTree(raw);
            recordUsage(res);
            return parse(res);
        } catch (ResourceAccessException ex) {
            throw new LlmUnavailableException("agent provider 연결 불가: " + name(), ex);
        } catch (HttpStatusCodeException ex) {
            // 4xx는 요청 형식 문제일 수 있어 응답 body를 남겨 원인 진단을 돕는다(키 등 민감정보는 body에 없음).
            log.warn("agent provider HTTP {} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new LlmResponseException(
                    "agent provider HTTP 오류: " + name() + " status=" + ex.getStatusCode().value(), ex);
        }
    }

    // usage.prompt_tokens/completion_tokens → 인터랙션 토큰 누적(provider 미제공 시 null)
    private void recordUsage(JsonNode res) {
        if (telemetry == null || res == null) {
            return;
        }
        JsonNode u = res.path("usage");
        if (u.isMissingNode() || u.isNull()) {
            return;
        }
        Integer prompt = u.has("prompt_tokens") ? u.get("prompt_tokens").asInt() : null;
        Integer completion = u.has("completion_tokens") ? u.get("completion_tokens").asInt() : null;
        telemetry.recordTokens(prompt, completion);
    }

    /** 응답 본문(String)을 Jackson 2 트리로 파싱. 비었거나 깨지면 응답 오류로 분류. */
    private JsonNode readTree(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new LlmResponseException("agent provider 응답 body가 비어 있음: " + name());
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new LlmResponseException("agent provider 응답 JSON 파싱 실패: " + name(), e);
        }
    }

    /**
     * AgentMessage 목록 → OpenAI messages 배열
     */
    private List<Map<String, Object>> toWireMessages(List<AgentMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentMessage m : messages) {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("role", m.role());
            boolean assistantToolCalls = "assistant".equals(m.role())
                    && m.toolCalls() != null && !m.toolCalls().isEmpty();
            if (assistantToolCalls) {
                // OpenAI/Groq 규약: tool_calls가 있으면 content는 생략(null) 가능.
                // 빈 문자열 content는 일부 provider(Groq)가 400으로 거부 → 내용이 있을 때만 싣는다.
                if (StringUtils.hasText(m.content())) {
                    wire.put("content", m.content());
                }
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    tcs.add(Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.name(),
                                    "arguments", tc.argumentsJson())));  // OpenAI: arguments=문자열
                }
                wire.put("tool_calls", tcs);
            } else {
                // assistant 직답·user·system·tool: content 필수 → null이면 빈 문자열로 안전화
                wire.put("content", m.content() != null ? m.content() : "");
            }
            if ("tool".equals(m.role())) {
                wire.put("tool_call_id", m.toolCallId());
            }
            out.add(wire);
        }
        return out;
    }

    /**
     * ToolSpec 목록 → OpenAI tools 배열
     */
    private List<Map<String, Object>> toWireTools(List<ToolSpec> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolSpec t : tools) {
            out.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", t.name(),
                            "description", t.description(),
                            "parameters", t.parameters())));
        }
        return out;
    }

    /**
     * choices[0].message → content + tool_calls
     */
    private AgentTurn parse(JsonNode res) {
        if (res == null || !res.path("choices").isArray() || res.path("choices").isEmpty()) {
            throw new LlmResponseException("agent provider 응답에 choices가 없음: " + name());
        }
        JsonNode message = res.path("choices").get(0).path("message");
        String content = message.path("content").isNull() ? "" : message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            for (JsonNode tc : tcArray) {
                JsonNode fn = tc.path("function");
                toolCalls.add(new ToolCall(
                        tc.path("id").asText(""),
                        fn.path("name").asText(""),
                        fn.path("arguments").asText("{}")));  // 이미 문자열
            }
        }
        // tool_calls 필드가 비고 content에 도구 호출 JSON만 흘린 경우 → 실제 호출로 승격(raw 노출 방지).
        if (toolCalls.isEmpty() && StringUtils.hasText(content)) {
            List<ToolCall> recovered = ToolCallEcho.recover(content, objectMapper);
            if (!recovered.isEmpty()) {
                return new AgentTurn(name(), "", recovered);
            }
        }
        return new AgentTurn(name(), content, toolCalls);
    }
}
