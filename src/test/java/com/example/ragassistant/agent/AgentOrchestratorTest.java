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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentOrchestrator 수용 기준 검증 (DB·LLM 없이 Mockito로 결정적).
 * AgentChatClient를 스크립트해 도구 호출/최종 답 턴을 흉내내고,
 * ToolRegistry로 도구 결과(+출처)를 주입한다.
 * AC↔테스트 1:1: @DisplayName의 AC-xx로 추적.
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    AgentChatClient agentChatClient;
    @Mock
    ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 설정 헬퍼 — 히스토리 상한만 가변, 나머지는 기본값. (maxSteps, maxToolCalls, timeoutMs, order, ...)
     */
    private AgentProperties props(int maxHistoryTurns) {
        return new AgentProperties(5, 10, 180_000L, List.of("groq"),
                maxHistoryTurns, 6, 4000, 8000);
    }

    private AgentOrchestrator orchestrator(AgentProperties props) {
        return new AgentOrchestrator(agentChatClient, toolRegistry, props, objectMapper);
    }

    @Test
    @DisplayName("read_document 본문 요청 → 그 문서 근거로 grounded=true")
    void readDocument_grounded() {
        when(toolRegistry.specs()).thenReturn(List.of());
        ToolCall call = new ToolCall("c1", "read_document", "{\"documentId\":42}");
        when(agentChatClient.chat(anyList(), anyList(), any()))
                .thenReturn(new AgentTurn("groq", "", List.of(call)),
                        new AgentTurn("groq", "문서 본문에 근거한 답", List.of()));
        SourceCitation src = new SourceCitation("ARCHITECTURE.md", 42L, "본문 일부", 1.0);
        when(toolRegistry.execute(eq("read_document"), any()))
                .thenReturn(new ToolResult("문서 본문", List.of(src)));

        AgentResponse res = orchestrator(props(6)).run("ARCHITECTURE.md 본문 보여줘", "groq");

        assertThat(res.stopReason()).isEqualTo("FINAL");
        assertThat(res.grounded()).isTrue();
        assertThat(res.sources()).extracting(SourceCitation::documentName).containsExactly("ARCHITECTURE.md");
        assertThat(res.steps()).extracting(AgentStep::tool).contains("read_document");
    }

    @Test
    @DisplayName("summarize_document 호출 → 요약 답 + grounded")
    void summarizeDocument() {
        when(toolRegistry.specs()).thenReturn(List.of());
        ToolCall call = new ToolCall("c1", "summarize_document", "{\"documentId\":42}");
        when(agentChatClient.chat(anyList(), anyList(), any()))
                .thenReturn(new AgentTurn("groq", "", List.of(call)),
                        new AgentTurn("groq", "문서 전체 요약입니다.", List.of()));
        SourceCitation src = new SourceCitation("ARCHITECTURE.md", 42L, "요약 근거", 1.0);
        when(toolRegistry.execute(eq("summarize_document"), any()))
                .thenReturn(new ToolResult("요약 텍스트", List.of(src)));

        AgentResponse res = orchestrator(props(6)).run("ARCHITECTURE.md 전체 요약해줘", "groq");

        assertThat(res.answer()).isEqualTo("문서 전체 요약입니다.");
        assertThat(res.grounded()).isTrue();
        assertThat(res.steps()).extracting(AgentStep::tool).contains("summarize_document");
        verify(toolRegistry).execute(eq("summarize_document"), any());
    }

    @Test
    @DisplayName("멀티턴 — 이전 대화가 현재 user 앞에 시드된다")
    @SuppressWarnings("unchecked")
    void multiTurnMemorySeeded() {
        when(toolRegistry.specs()).thenReturn(List.of());
        ArgumentCaptor<List<AgentMessage>> cap = ArgumentCaptor.forClass(List.class);
        when(agentChatClient.chat(cap.capture(), anyList(), any()))
                .thenReturn(new AgentTurn("groq", "답", List.of()));
        List<ConversationTurn> history = List.of(
                new ConversationTurn("user", "환불 정책 알려줘"),
                new ConversationTurn("assistant", "유료 플랜은 14일 이내 가능"));

        orchestrator(props(6)).run("그럼 14일 지나면?", "groq", history);

        List<AgentMessage> sent = cap.getValue();
        assertThat(sent).extracting(AgentMessage::content)
                .containsSubsequence("환불 정책 알려줘", "유료 플랜은 14일 이내 가능", "그럼 14일 지나면?");
        assertThat(sent.get(0).role()).isEqualTo("system");
        assertThat(sent.get(sent.size() - 1).content()).isEqualTo("그럼 14일 지나면?");
    }

    @Test
    @DisplayName("히스토리 상한 — 최근 N턴만 시드(오래된 건 드롭)")
    @SuppressWarnings("unchecked")
    void historyCap() {
        when(toolRegistry.specs()).thenReturn(List.of());
        ArgumentCaptor<List<AgentMessage>> cap = ArgumentCaptor.forClass(List.class);
        when(agentChatClient.chat(cap.capture(), anyList(), any()))
                .thenReturn(new AgentTurn("groq", "답", List.of()));
        List<ConversationTurn> history = List.of(
                new ConversationTurn("user", "오래된질문1"),
                new ConversationTurn("assistant", "오래된답1"),
                new ConversationTurn("user", "최근질문2"),
                new ConversationTurn("assistant", "최근답2"));

        orchestrator(props(2)).run("현재질문", "groq", history);   // 상한 2턴

        List<AgentMessage> sent = cap.getValue();
        assertThat(sent).extracting(AgentMessage::content)
                .contains("최근질문2", "최근답2", "현재질문")
                .doesNotContain("오래된질문1", "오래된답1");
    }

    @Test
    @DisplayName("스트리밍 — step(tool_call→tool_result)* → delta* 순서, done 필드 동일")
    void streamingEventOrder() {
        when(toolRegistry.specs()).thenReturn(List.of());
        ToolCall call = new ToolCall("c1", "read_document", "{\"documentId\":42}");
        when(agentChatClient.chat(anyList(), anyList(), any()))
                .thenReturn(new AgentTurn("groq", "", List.of(call)),
                        new AgentTurn("groq", "답", List.of()));
        SourceCitation src = new SourceCitation("ARCHITECTURE.md", 42L, "근거", 1.0);
        when(toolRegistry.execute(eq("read_document"), any()))
                .thenReturn(new ToolResult("본문", List.of(src)));

        RecordingHandler handler = new RecordingHandler();
        AgentResponse res = orchestrator(props(6)).runStreaming("q", "groq", List.of(), handler);

        assertThat(handler.events)
                .containsSubsequence("toolCall:read_document", "toolResult:read_document", "delta");
        assertThat(res.stopReason()).isEqualTo("FINAL");
        assertThat(res.grounded()).isTrue();
        assertThat(res.sources()).extracting(SourceCitation::documentName).containsExactly("ARCHITECTURE.md");
    }

    /**
     * 스트리밍 이벤트 순서를 문자열로 기록하는 싱크.
     */
    private static final class RecordingHandler implements AgentStreamHandler {
        final List<String> events = new ArrayList<>();

        @Override
        public void onToolCall(int index, String tool, Map<String, Object> arguments) {
            events.add("toolCall:" + tool);
        }

        @Override
        public void onToolResult(int index, String tool, String resultSummary) {
            events.add("toolResult:" + tool);
        }

        @Override
        public void onDelta(String text) {
            events.add("delta");
        }
    }
}
