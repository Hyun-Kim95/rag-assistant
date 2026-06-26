package com.example.ragassistant.llm.agent;

import com.example.ragassistant.exception.LlmResponseException;
import com.example.ragassistant.exception.LlmUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama /api/chat tool calling 클라이언트.
 * OpenAI와 wire 차이:
 * - tool_calls[].function.arguments 가 '객체' → 저장 시 문자열로 직렬화, 전송 시 객체로 복원.
 * - tool_call id가 없음 → 수신 시 "call_{index}" 로 합성(내부 추적용).
 * 실패는 기존 LLM 예외로 던져 폴백·핸들러와 호환.
 */
public class OllamaAgentClient implements AgentChatClient {

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String name;
    private final double temperature;

    public OllamaAgentClient(RestClient ollamaRestClient, ObjectMapper objectMapper,
                             String model, String name, double temperature) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.name = name;
        this.temperature = temperature;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", toWireMessages(messages));
        List<Map<String, Object>> wireTools = toWireTools(tools);
        if (!wireTools.isEmpty()) {
            body.put("tools", wireTools);
        }
        body.put("stream", false);
        body.put("options", Map.of("temperature", temperature, "num_ctx", 8192));
        try {
            String raw = ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(readTree(raw));
        } catch (ResourceAccessException ex) {
            throw new LlmUnavailableException("agent provider 연결 불가: " + name, ex);
        } catch (HttpStatusCodeException ex) {
            throw new LlmResponseException(
                    "agent provider HTTP 오류: " + name + " status=" + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            // 응답 추출 단계 타임아웃 등도 '닿을 수 없음'으로 분류 → 폴백 대상
            throw new LlmUnavailableException("agent provider 응답 처리 실패: " + name, ex);
        }
    }

    private JsonNode readTree(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmResponseException("agent provider 응답 body가 비어 있음: " + name);
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new LlmResponseException("agent provider 응답 JSON 파싱 실패: " + name, e);
        }
    }

    private List<Map<String, Object>> toWireMessages(List<AgentMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentMessage m : messages) {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("role", m.role());
            wire.put("content", m.content() != null ? m.content() : "");
            if ("assistant".equals(m.role()) && m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> tcs = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", tc.name() != null ? tc.name() : "");
                    fn.put("arguments", parseArgsToObject(tc.argumentsJson()));    // Ollama: arguments=객체
                    tcs.add(Map.of("function", fn));
                }
                wire.put("tool_calls", tcs);
            }
            out.add(wire);
        }
        return out;
    }

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

    private Object parseArgsToObject(String argumentsJson) {
        // readValue("null"/null/"")은 null을 돌려줄 수 있어 Map.of(value=null) NPE를 유발 → 빈 맵으로 정규화
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(argumentsJson, Map.class);
            return parsed != null ? parsed : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private AgentTurn parse(JsonNode res) {
        if (res == null || res.path("message").isMissingNode()) {
            throw new LlmResponseException("agent provider 응답에 message가 없음: " + name);
        }
        JsonNode message = res.path("message");
        String content = message.path("content").asText("");
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            int i = 0;
            for (JsonNode tc : tcArray) {
                JsonNode fn = tc.path("function");
                String argsJson = fn.path("arguments").isMissingNode()
                        ? "{}"
                        : fn.path("arguments").toString();   // 객체 → 문자열로 정규화
                toolCalls.add(new ToolCall("call_" + i, fn.path("name").asText(""), argsJson));
                i++;
            }
        }
        // tool_calls 필드가 비고 content에 도구 호출 JSON만 흘린 경우 → 실제 호출로 승격(raw 노출 방지).
        if (toolCalls.isEmpty() && content != null && !content.isBlank()) {
            List<ToolCall> recovered = ToolCallEcho.recover(content, objectMapper);
            if (!recovered.isEmpty()) {
                return new AgentTurn(name, "", recovered);
            }
        }
        return new AgentTurn(name, content, toolCalls);
    }
}
