package com.example.ragassistant.agent;

import com.example.ragassistant.agent.tool.ToolRegistry;
import com.example.ragassistant.agent.tool.ToolResult;
import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.dto.AgentResponse;
import com.example.ragassistant.dto.AgentStep;
import com.example.ragassistant.dto.ConversationTurn;
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

            [도구]
            - list_documents: 어떤 문서가 올라와 있는지(파일명·개수) 확인.
            - search_documents: 문서 내용에서 질문과 관련된 부분을 검색.
            - read_document: 특정 문서의 일부를 자세히 읽기.
            - summarize_document: 특정 문서 하나를 요약.

            [호출 규칙]
            - 문서 목록/어떤 문서가 있는지 묻는 질문: list_documents 를 한 번만 호출하고, 그 파일명 목록을 바로 답한다. 이때 search_documents·summarize_document 를 추가로 호출하지 않는다.
            - 문서 내용을 묻는 질문: 먼저 search_documents 로 검색한다.
            - summarize_document·read_document 는 사용자가 특정 문서를 지목해 요약/상세를 요청할 때만, 그 문서 하나에 대해 호출한다. 모든 문서를 자동으로 요약하지 않는다.
            - 충분한 결과를 얻으면 더 이상 도구를 호출하지 말고 바로 답한다. 같은 도구를 같은 인자로 반복 호출하지 않는다.

            [작성 규칙]
            - 검색/읽기 결과에 '실제로 나온 내용'만 사용하고, 없는 사실을 지어내지 않는다.
            - 근거가 없으면 "관련 내용을 찾을 수 없습니다"라고 솔직히 답한다. (단, 문서 목록 질문은 list_documents 결과로 답하면 된다.)
            - 간결한 한국어로 쓰고, 같은 문장을 반복하지 않으며, 불필요한 영어·중국어를 섞지 않는다.
            """;

    private static final String TOOL_BUDGET_FALLBACK = "여러 번 도구를 사용했지만 답을 마무리하지 못했습니다. 질문을 더 구체적으로 다시 시도해 주세요.";
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
        return loop(message, provider, List.of(), AgentStreamHandler.NOOP);     // 단발
    }

    public AgentResponse run(String message, String provider, List<ConversationTurn> history) {
        return loop(message, provider, history, AgentStreamHandler.NOOP);       // 멀티턴(동기)
    }

    /**
     * 스트리밍: 도구 호출/결과를 handler로 실시간 방출하고 최종 답을 delta로 흘린다.
     */
    public AgentResponse runStreaming(String message, String provider, List<ConversationTurn> history, AgentStreamHandler handler) {
        return loop(message, provider, history, handler);
    }

    private AgentResponse loop(String message, String provider, List<ConversationTurn> history, AgentStreamHandler handler) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message가 비어 있습니다.");
        }

        List<AgentMessage> conv = new ArrayList<>();
        conv.add(AgentMessage.system(SYSTEM));
        seedHistory(conv, history);                                 // T-B: 이전 대화(최근 N턴)
        conv.add(AgentMessage.user(message.trim()));

        List<ToolSpec> tools = toolRegistry.specs();
        List<AgentStep> steps = new ArrayList<>();
        Map<Long, SourceCitation> sourcesByChunk = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + props.timeoutMs();
        String usedProvider = null;
        int toolCalls = 0;
        boolean budgetHit = false;

        for (int step = 1; step <= props.maxSteps() && !budgetHit; step++) {
            if (System.currentTimeMillis() > deadline) {
                return finalize(TIMEOUT_FALLBACK, sourcesByChunk, usedProvider, "TIMEOUT", steps);
            }

            AgentTurn turn = agentChatClient.chat(conv, tools, provider);
            usedProvider = turn.provider();

            if (!turn.hasToolCalls()) {
                emitDeltas(handler, turn.content());                // 최종 답을 조각내어 흘림
                return finalize(turn.content(), sourcesByChunk, usedProvider, "FINAL", steps);
            }

            conv.add(AgentMessage.assistant(turn.content(), turn.toolCalls()));
            // 예산 내에서 '실제 실행할' 도구만 assistant 메시지에 남긴다.
            // tool_calls와 tool 응답 개수가 어긋나면 일부 provider(Groq 등)가 400으로 거부 → 강제 종결이 빈 응답이 됨.
            List<ToolCall> calls = turn.toolCalls();
            int remaining = props.maxToolCalls() - toolCalls;
            if (calls.size() > remaining) {
                calls = calls.subList(0, Math.max(0, remaining));
                budgetHit = true;                                   // 남는 건 버리고 다음 루프는 중단(강제 종결로)
            }
            conv.add(AgentMessage.assistant(turn.content(), calls));
            for (ToolCall tc : calls) {
                JsonNode args = parseArgs(tc.argumentsJson());
                Map<String, Object> argMap = toMap(args);
                int index = steps.size() + 1;
                handler.onToolCall(index, tc.name(), argMap);       // step: tool_call

                ToolResult result = safeExecute(tc.name(), args);
                result.sources().forEach(s -> sourcesByChunk.putIfAbsent(s.chunkId(), s));
                String summary = summarize(result);
                steps.add(new AgentStep(index, tc.name(), argMap, summary));
                handler.onToolResult(index, tc.name(), summary);    // step: tool_result

                conv.add(AgentMessage.tool(tc.id(), result.content()));
                toolCalls++;
            }
        }

        // max-steps 또는 도구 예산 소진 → 도구 없이 마지막으로 답을 강제(추가 루프 차단)
        return forceFinalAnswer(conv, usedProvider, sourcesByChunk, steps, handler,
                budgetHit ? "TOOL_BUDGET" : "MAX_STEPS");
    }

    /**
     * 안전장치 발동 시 마지막 한 번을 '도구 없이' 호출해 수집한 정보로 답을 종결한다.
     * tools=[]라 모델이 더 이상 도구를 못 부르고 텍스트로 답한다(런어웨이 차단).
     * 모델이 실제 답을 내면 grounded 유지 위해 FINAL로 확정한다.
     */
    private AgentResponse forceFinalAnswer(List<AgentMessage> conv, String provider,
                                           Map<Long, SourceCitation> sources, List<AgentStep> steps,
                                           AgentStreamHandler handler, String reason) {
        String answer;
        try {
            conv.add(AgentMessage.user(
                    "도구를 더 호출하지 말고, 지금까지 수집한 정보만으로 한국어로 답하세요. 정보가 부족하면 솔직히 모른다고 답하세요."));
            AgentTurn turn = agentChatClient.chat(conv, List.of(), provider);   // tools 없음 → 강제 종결
            answer = StringUtils.hasText(turn.content())
                    ? turn.content()
                    : ("TOOL_BUDGET".equals(reason) ? TOOL_BUDGET_FALLBACK : MAX_STEPS_FALLBACK);
        } catch (Exception e) {
            log.warn("강제 종결 응답 실패: {}", e.toString());
            answer = "TOOL_BUDGET".equals(reason) ? TOOL_BUDGET_FALLBACK : MAX_STEPS_FALLBACK;
        }
        emitDeltas(handler, answer);
        return finalize(answer, sources, provider, "FINAL", steps);
    }

    /**
     * 최종 답을 일정 크기로 잘라 delta로 방출.
     * MVP: tool-calling 응답은 토큰 스트리밍이 까다로워, 최종 답 '생성 완료 후' 조각 전송한다.
     * (실시간 체감의 핵심인 도구 호출 step은 이미 생성 도중 실시간 방출된다.)
     * 진짜 토큰 스트리밍은 후속(transport streaming) 과제.
     */
    private void emitDeltas(AgentStreamHandler handler, String answer) {
        if (handler == AgentStreamHandler.NOOP || !StringUtils.hasText(answer)) {
            return;
        }
        int size = 24;
        for (int i = 0; i < answer.length(); i += size) {
            handler.onDelta(answer.substring(i, Math.min(i + size, answer.length())));
        }
    }

    private void seedHistory(List<AgentMessage> conv, List<ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return;
        }
        int from = Math.max(0, history.size() - props.maxHistoryTurns());
        for (ConversationTurn t : history.subList(from, history.size())) {
            if (t == null || !StringUtils.hasText(t.role()) || !StringUtils.hasText(t.content())) {
                continue;
            }
            String role = t.role().trim().toLowerCase();
            if ("user".equals(role)) {
                conv.add(AgentMessage.user(t.content()));
            } else if ("assistant".equals(role)) {
                conv.add(AgentMessage.assistant(t.content(), null));
            }
        }
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
