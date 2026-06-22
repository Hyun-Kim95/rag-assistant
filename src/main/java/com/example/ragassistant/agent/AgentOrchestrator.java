package com.example.ragassistant.agent;

import com.example.ragassistant.agent.tool.ToolRegistry;
import com.example.ragassistant.agent.tool.ToolResult;
import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.dto.AgentResponse;
import com.example.ragassistant.dto.AgentStep;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.llm.agent.AgentChatClient;
import com.example.ragassistant.llm.agent.AgentMessage;
import com.example.ragassistant.llm.agent.AgentTurn;
import com.example.ragassistant.llm.agent.ToolCall;
import com.example.ragassistant.llm.agent.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * tool calling agent 루프.
 * system+user로 시작 → (모델 호출 → tool_calls 있으면 실행·되먹임)* → 최종 답.
 * 안전장치: max-steps(무한 루프 방지)·timeout·tool 오류는 텍스트로 되먹여 모델이 복구.
 * grounded: search 도구가 출처를 만들었으면 true.
 */
@Service
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private static final String SYSTEM = """
            당신은 업로드된 문서만 근거로 답하는 한국어 어시스턴트입니다.

            [도구 사용 규칙]
            - 문서 내용이 필요한 질문은 반드시 먼저 search_documents 로 검색한다.
            - 답변은 검색 결과(snippet)에 '실제로 나온 내용'만 사용한다. 검색 결과에 없는 이유·사실·항목을 지어내지 않는다.
            - 검색 결과에 답의 근거가 없으면 "관련 내용을 찾을 수 없습니다"라고 솔직히 답한다.
            - 어떤 문서가 올라와 있는지 물으면 list_documents 로 목록을 확인한다.

            [작성 규칙]
            - 간결한 한국어로 쓰고, 같은 문장을 반복하지 않는다.
            - 중국어나 불필요한 영어 문장을 섞지 않는다.
            """;

    private static final String MAX_STEPS_FALLBACK = "단계 한도에 도달해 답을 마무리하지 못했습니다. 질문을 더 구체적으로 다시 시도해 주세요.";
    private static final String TIMEOUT_FALLBACK = "처리 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
    /**
     * 모델이 '근거 없음'으로 답했는지 가늠하는 힌트(공백 제거 후 부분일치). no-answer면 출처를 비운다.
     */
    private static final List<String> NO_ANSWER_HINTS = List.of(
            "찾을수없", "찾지못", "문서가없", "정보가없", "관련내용이없",
            "관련내용을찾", "모르겠", "알수없", "확인할수없");

    private final AgentChatClient agentChatClient;
    private final ToolRegistry toolRegistry;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(AgentChatClient agentChatClient, ToolRegistry toolRegistry,
                             AgentProperties props, ObjectMapper objectMapper) {
        this.agentChatClient = agentChatClient;
        this.toolRegistry = toolRegistry;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public AgentResponse run(String message, String provider) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message가 비어 있습니다.");
        }

        List<AgentMessage> history = new ArrayList<>();
        history.add(AgentMessage.system(SYSTEM));
        history.add(AgentMessage.user(message.trim()));

        List<ToolSpec> tools = toolRegistry.specs();
        List<AgentStep> steps = new ArrayList<>();
        Map<Long, SourceCitation> sourcesByChunk = new LinkedHashMap<>();   // chunkId로 중복 제거
        long deadline = System.currentTimeMillis() + props.timeoutMs();
        String usedProvider = null;
        String lastContent = "";

        for (int step = 1; step <= props.maxSteps(); step++) {
            if (System.currentTimeMillis() > deadline) {
                return finalize(TIMEOUT_FALLBACK, sourcesByChunk, usedProvider, "TIMEOUT", steps);
            }

            // 전 provider 불가 시 AllProvidersUnavailableException → 503(LLM_ALL_PROVIDERS_UNAVAILABLE) 전파
            AgentTurn turn = agentChatClient.chat(history, tools, provider);
            usedProvider = turn.provider();
            if (StringUtils.hasText(turn.content())) {
                lastContent = turn.content();
            }

            if (!turn.hasToolCalls()) {
                // 도구 호출 없음 → 최종 답
                return finalize(turn.content(), sourcesByChunk, usedProvider, "FINAL", steps);
            }

            // 도구 호출 턴: assistant 메시지(+tool_calls)를 히스토리에 기록
            history.add(AgentMessage.assistant(turn.content(), turn.toolCalls()));
            for (ToolCall tc : turn.toolCalls()) {
                JsonNode args = parseArgs(tc.argumentsJson());
                ToolResult result = safeExecute(tc.name(), args);
                result.sources().forEach(s -> sourcesByChunk.putIfAbsent(s.chunkId(), s));
                steps.add(new AgentStep(steps.size() + 1, tc.name(), toMap(args), summarize(result)));
                history.add(AgentMessage.tool(tc.id(), result.content()));
            }
        }
        // 루프 소진 → best-effort
        String answer = StringUtils.hasText(lastContent) ? lastContent : MAX_STEPS_FALLBACK;
        return finalize(answer, sourcesByChunk, usedProvider, "MAX_STEPS", steps);
    }

    /**
     * 도구 실행 중 예기치 못한 예외도 텍스트로 되먹여 모델이 복구하게 한다.
     */
    private ToolResult safeExecute(String name, JsonNode args) {
        try {
            return toolRegistry.execute(name, args);
        } catch (Exception e) {
            log.warn("도구 실행 실패: tool={} cause={}", name, e.toString());
            return ToolResult.text("도구 실행 오류(" + name + "): " + e.getMessage());
        }
    }

    /**
     * 응답 확정. 다음 경우엔 출처·grounded를 비운다(answer와의 모순 방지):
     * - 비정상 종료(TIMEOUT·MAX_STEPS): answer가 fallback 안내문이라 근거가 아님.
     * - FINAL이지만 모델이 '근거 없음'으로 답한 경우: search가 무관한 chunk를 반환했어도 인용하지 않음.
     * 기존 RagService의 `grounded ? sources : List.of()` 정렬과 같은 철학.
     */
    private AgentResponse finalize(String answer, Map<Long, SourceCitation> sources,
                                   String provider, String stopReason, List<AgentStep> steps) {
        boolean noAnswer = !"FINAL".equals(stopReason) || isNoAnswer(answer);
        List<SourceCitation> sourceList = noAnswer ? List.of() : new ArrayList<>(sources.values());
        boolean grounded = !sourceList.isEmpty();
        return new AgentResponse(answer, sourceList, grounded, provider, stopReason, steps);
    }

    /**
     * 공백 제거 후 no-answer 힌트가 들어 있으면 근거 없는 답으로 본다.
     */
    private static boolean isNoAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return true;
        }
        String compact = answer.replaceAll("\\s+", "");
        return NO_ANSWER_HINTS.stream().anyMatch(compact::contains);
    }

    private JsonNode parseArgs(String argumentsJson) {
        try {
            return objectMapper.readTree(StringUtils.hasText(argumentsJson) ? argumentsJson : "{}");
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode args) {
        try {
            return objectMapper.convertValue(args, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String summarize(ToolResult r) {
        if (!r.sources().isEmpty()) {
            return r.sources().size() + " sources";
        }
        String c = r.content() == null ? "" : r.content();
        return c.length() <= 80 ? c : c.substring(0, 80) + "...";
    }
}
