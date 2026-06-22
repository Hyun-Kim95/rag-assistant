package com.example.ragassistant.controller;

import com.example.ragassistant.agent.AgentOrchestrator;
import com.example.ragassistant.agent.AgentStreamHandler;
import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.dto.AgentRequest;
import com.example.ragassistant.dto.AgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 스트리밍 에이전트. step(tool_call/tool_result) → delta(최종 답) → done 순으로 SSE 방출.
 * 폴백 없이 default 라우팅(기존 /api/chat/stream과 동일 정책, D6).
 */
@Tag(name = "Agent", description = "tool calling 기반 에이전트")
@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamController.class);

    private final AgentOrchestrator orchestrator;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final ExecutorService agentStreamExecutor;

    public AgentStreamController(AgentOrchestrator orchestrator, AgentProperties props,
                                 ObjectMapper objectMapper, ExecutorService agentStreamExecutor) {
        this.orchestrator = orchestrator;
        this.props = props;
        this.objectMapper = objectMapper;
        this.agentStreamExecutor = agentStreamExecutor;
    }

    @Operation(summary = "에이전트 질의(스트리밍)", description = "도구 호출(step)과 최종 답(delta)을 SSE로 흘린다")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AgentRequest request) {
        // 빈 message는 스트림을 열기 전 400(GlobalExceptionHandler가 IllegalArgumentException 매핑)
        if (!StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("message가 비어 있습니다.");
        }

        SseEmitter emitter = new SseEmitter(props.timeoutMs() + 60_000L);
        agentStreamExecutor.execute(() -> {
            try {
                AgentStreamHandler handler = new AgentStreamHandler() {
                    @Override
                    public void onToolCall(int index, String tool, Map<String, Object> arguments) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("index", index);
                        data.put("phase", "tool_call");
                        data.put("tool", tool);
                        data.put("arguments", arguments);
                        send(emitter, "step", data);
                    }

                    @Override
                    public void onToolResult(int index, String tool, String resultSummary) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("index", index);
                        data.put("phase", "tool_result");
                        data.put("tool", tool);
                        data.put("resultSummary", resultSummary);
                        send(emitter, "step", data);
                    }

                    @Override
                    public void onDelta(String text) {
                        send(emitter, "delta", Map.of("text", text));
                    }
                };

                AgentResponse res = orchestrator.runStreaming(
                        request.message(), request.provider(), request.messages(), handler);
                send(emitter, "done", res);
                emitter.complete();
            } catch (Exception e) {
                log.warn("agent stream 오류: {}", e.toString());
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", e.getClass().getSimpleName());
                err.put("message", e.getMessage() != null ? e.getMessage() : "에이전트 처리 중 오류");
                send(emitter, "error", err);
                emitter.complete();
            }
        });
        return emitter;
    }

    /**
     * payload를 JSON 문자열로 직렬화해 SSE data로 전송(메시지 컨버터 모호성 회피).
     */
    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(objectMapper.writeValueAsString(payload), MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.debug("SSE send 실패(event={}): {}", event, e.toString());    // 연결 종료 등은 무시
        }
    }
}
