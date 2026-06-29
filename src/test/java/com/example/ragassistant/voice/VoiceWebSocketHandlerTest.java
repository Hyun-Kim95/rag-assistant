package com.example.ragassistant.voice;

import com.example.ragassistant.agent.AgentOrchestrator;
import com.example.ragassistant.agent.AgentStreamHandler;
import com.example.ragassistant.domain.CallTurn;
import com.example.ragassistant.dto.AgentResponse;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.repository.CallLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VoiceWebSocketHandler 수용 기준 검증 (DB·LLM·브라우저 없이 Mockito로 결정적).
 * AgentOrchestrator.runStreaming을 스크립트해 delta·최종 AgentResponse를 흉내내고,
 * WebSocketSession 송신을 캡처해 VoiceEvent(JSON)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceWebSocketHandlerTest {

    @Mock
    AgentOrchestrator agentOrchestrator;
    @Mock
    WebSocketSession session;
    @Mock
    GoogleTtsService googleTtsService; // 기본 synthesize=null → 핸들러가 tts.fallback 경로
    @Mock
    CallLogRepository callLogRepository;
    @Mock
    GroqSttService sttService; // 기본 available()=false → 브라우저 텍스트 경로(하위호환)

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PiiMasker piiMasker = new PiiMasker(); // 실제 마스킹 검증
    private VoiceWebSocketHandler handler;
    private List<String> sentPayloads;

    @BeforeEach
    void setUp() throws Exception {
        // executor는 테스트에서 동기 실행: submit된 Runnable을 즉시 호출
        java.util.concurrent.ExecutorService syncExecutor = new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
        };
        handler = new VoiceWebSocketHandler(agentOrchestrator, objectMapper, syncExecutor, googleTtsService,
                callLogRepository, piiMasker, sttService);

        Map<String, Object> attributes = new HashMap<>();
        sentPayloads = new ArrayList<>();
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(callLogRepository.createSession(any())).thenReturn(1L);
        lenient().doAnswer(inv -> {
            sentPayloads.add(((TextMessage) inv.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));

        handler.afterConnectionEstablished(session); // 초기 LISTENING
    }

    // --- 헬퍼 ---

    private void utter(String text) throws Exception {
        handler.handleTextMessage(session, new TextMessage(
                objectMapper.writeValueAsString(Map.of("type", "user_utterance", "text", text))));
    }

    private void utter(String text, int sttMs) throws Exception {
        handler.handleTextMessage(session, new TextMessage(
                objectMapper.writeValueAsString(Map.of("type", "user_utterance", "text", text, "sttMs", sttMs))));
    }

    /** 발화 오디오(바이너리) 프레임 수신 시뮬레이션. */
    private void sendAudio(byte[] audio) {
        handler.handleBinaryMessage(session, new BinaryMessage(audio));
    }

    /** 오디오 동반 발화(hasAudio:true): 클라우드 STT 경로 트리거. text는 브라우저 폴백 텍스트. */
    private void utterWithAudio(String browserText) throws Exception {
        handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(
                Map.of("type", "user_utterance", "text", browserText, "hasAudio", true))));
    }

    /** runStreaming에 전달된 사용자 텍스트(전사 확정값) 캡처. */
    private String capturedUserText() {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(agentOrchestrator).runStreaming(c.capture(), any(), any(), any());
        return c.getValue();
    }

    /**
     * runStreaming 스텁: delta(있으면) 방출 후 주어진 AgentResponse 반환
     */
    private void stubAnswer(String delta, AgentResponse response) {
        when(agentOrchestrator.runStreaming(any(), any(), any(), any())).thenAnswer(inv -> {
            AgentStreamHandler h = inv.getArgument(3);
            if (delta != null) h.onDelta(delta);
            return response;
        });
    }

    private static AgentResponse grounded(String answer, SourceCitation source) {
        return new AgentResponse(answer, List.of(source), true, "groq", "FINAL", List.of());
    }

    private static AgentResponse noAnswer(String answer) {
        return new AgentResponse(answer, List.of(), false, null, "FINAL", List.of());
    }

    private List<JsonNode> events() {
        List<JsonNode> out = new ArrayList<>();
        for (String p : sentPayloads) {
            try {
                out.add(objectMapper.readTree(p));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private List<String> states() {
        return events().stream()
                .filter(e -> "state".equals(e.path("event").asText()))
                .map(e -> e.path("state").asText())
                .toList();
    }

    private JsonNode firstEvent(String name) {
        return events().stream()
                .filter(e -> name.equals(e.path("event").asText()))
                .findFirst().orElse(null);
    }

    // --- STT 부분→최종 (manual: 브라우저 Web Speech API) ---
    @Test
    @Disabled("STT partial/final은 브라우저 Web Speech API 영역 — 수동 검증(manual)")
    @DisplayName("발화 시 stt.partial 1회 이상 + stt.final 1회 (브라우저, manual)")
    void sttPartialThenFinal_manual() {
        // Google STT 도입 시 서버 STT 이벤트로 자동화 전환.
    }

    // --- agent 연결 (멀티턴 tool calling) ---
    @Test
    @DisplayName("user_utterance → answer.delta 수신 + answer.done에 grounded·sources 포함")
    void agentConnected() throws Exception {
        stubAnswer("환불은 ",
                grounded("환불은 14일 이내 신청.",
                        new SourceCitation("faq.md", 837L, "## 환불 정책", 0.99)));

        utter("환불 정책 알려줘");

        assertThat(firstEvent("answer.delta")).isNotNull();
        assertThat(firstEvent("answer.delta").path("text").asText()).isEqualTo("환불은 ");

        JsonNode done = firstEvent("answer.done");
        assertThat(done).isNotNull();
        assertThat(done.path("grounded").asBoolean()).isTrue();
        assertThat(done.path("sources")).isNotEmpty();
        assertThat(done.path("answer").asText()).contains("14일");
    }

    // --- TTS 재생 트리거 (서버 전이부) ---
    @Test
    @DisplayName("grounded 답변 후 SPEAKING으로 전이 (실제 재생·LISTENING 복귀는 브라우저 manual)")
    void speakingTransition() throws Exception {
        stubAnswer("답변", grounded("답변입니다.", new SourceCitation("doc.md", 1L, "...", 0.9)));

        utter("질문");

        // 순서: THINKING → (delta/done) → SPEAKING
        assertThat(states()).containsSubsequence("THINKING", "SPEAKING");
    }

    // --- no-answer 상태 ---
    @Test
    @DisplayName("검색 hit 없음 → answer.done grounded=false, 1회차는 handoff 아님(통화 계속)")
    void noAnswerKeepsCall() throws Exception {
        stubAnswer(null, noAnswer("문서에서 확인할 수 없는 질문입니다."));

        utter("2025년 매출 알려줘");

        JsonNode done = firstEvent("answer.done");
        assertThat(done).isNotNull();
        assertThat(done.path("grounded").asBoolean()).isFalse();
        // 1회 no-answer는 전환 임계 미만 → handoff 없음, SPEAKING으로 계속
        assertThat(firstEvent("handoff")).isNull();
        assertThat(states()).contains("SPEAKING");
    }

    // --- 멀티턴 메모리: 2번째 발화에 이전 대화 이력이 전달되는지 ---
    @Test
    @DisplayName("두 번째 발화 시 runStreaming에 직전 user/assistant 이력이 함께 전달")
    void multiturnHistoryPassed() throws Exception {
        stubAnswer("답", grounded("기술 스택은 Spring Boot 입니다.",
                new SourceCitation("README.md", 1L, "스택", 0.9)));

        utter("기술 스택 알려줘");
        utter("그게 다인가요");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.example.ragassistant.dto.ConversationTurn>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        // 2회 호출 → 마지막(2번째) 호출 시점의 history에는 1턴(user+assistant)이 들어 있어야 함
        verify(agentOrchestrator, org.mockito.Mockito.times(2))
                .runStreaming(any(), any(), historyCaptor.capture(), any());
        List<com.example.ragassistant.dto.ConversationTurn> historyAtSecondCall = historyCaptor.getAllValues().get(1);
        assertThat(historyAtSecondCall).extracting(com.example.ragassistant.dto.ConversationTurn::role)
                .contains("user", "assistant");
        assertThat(historyAtSecondCall).extracting(com.example.ragassistant.dto.ConversationTurn::content)
                .contains("기술 스택 알려줘");
    }

    @Test
    @DisplayName("no-answer 턴은 이력에 쌓이지 않아 다음 발화를 오염시키지 않는다")
    void noAnswerTurnNotKeptInHistory() throws Exception {
        stubAnswer(null, noAnswer("문서에서 확인할 수 없는 질문입니다."));

        utter("환불 정책 알려줘");    // no-answer → 이력에 남기면 안 됨
        utter("청크 사이즈 알려줘");   // 직전 no-answer에 오염되지 않아야 함

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.example.ragassistant.dto.ConversationTurn>> historyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(agentOrchestrator, org.mockito.Mockito.times(2))
                .runStreaming(any(), any(), historyCaptor.capture(), any());
        // 첫 턴이 no-answer라 이력에 쌓이지 않으므로, 두 번째 호출 시점 history는 비어 있어야 한다.
        assertThat(historyCaptor.getAllValues().get(1)).isEmpty();
    }

    // --- 마스킹 저장 ---
    @Test
    @DisplayName("턴 종료 시 user_text_masked에 PII(전화번호)가 마스킹되어 저장")
    void maskedPersistence() throws Exception {
        stubAnswer(null, noAnswer("문서에서 확인할 수 없는 질문입니다."));

        utter("내 번호는 010-1234-5678 입니다");

        ArgumentCaptor<CallTurn> captor = ArgumentCaptor.forClass(CallTurn.class);
        verify(callLogRepository).saveTurn(captor.capture());
        CallTurn turn = captor.getValue();
        assertThat(turn.userTextMasked()).doesNotContain("010-1234-5678");
        assertThat(turn.userTextMasked()).contains("[전화번호]");
    }

    // --- 구간 지연 로깅 ---
    @Test
    @DisplayName("턴 종료 시 stt_ms/llm_ms/tts_ms/ttfb_ms가 모두 0 이상 기록(stt_ms는 클라값 보존)")
    void latencyMetrics() throws Exception {
        stubAnswer("부분", grounded("답변입니다.", new SourceCitation("doc.md", 1L, "...", 0.9)));

        utter("질문 내용입니다", 120);

        ArgumentCaptor<CallTurn> captor = ArgumentCaptor.forClass(CallTurn.class);
        verify(callLogRepository).saveTurn(captor.capture());
        CallTurn turn = captor.getValue();
        assertThat(turn.sttMs()).isEqualTo(120);
        assertThat(turn.llmMs()).isGreaterThanOrEqualTo(0);
        assertThat(turn.ttsMs()).isGreaterThanOrEqualTo(0);
        assertThat(turn.ttfbMs()).isGreaterThanOrEqualTo(0);
    }

    // --- STT: 브라우저 1차 + 클라우드(Groq) 폴백 ---

    @Test
    @DisplayName("브라우저 인식이 있으면 브라우저 텍스트 사용 — 클라우드 미호출(오작동 덮어쓰기 방지)")
    void browserTextPreferredOverCloud() throws Exception {
        stubAnswer("환불은 ", grounded("환불은 14일 이내 신청.",
                new SourceCitation("faq.md", 1L, "## 환불", 0.9)));

        sendAudio(new byte[]{1, 2, 3, 4});
        utterWithAudio("환불 정책 알려줘");   // 브라우저 인식 정상 → 그대로 사용

        assertThat(capturedUserText()).isEqualTo("환불 정책 알려줘");
        verify(sttService, never()).transcribe(any(), any());   // 브라우저 인식 있으니 클라우드 호출 안 함
        assertThat(firstEvent("stt.final")).isNull();           // 보정 이벤트 없음
    }

    @Test
    @DisplayName("브라우저 인식이 비면 클라우드(Groq) 전사로 폴백 + stt.final 표시")
    void cloudFallbackWhenBrowserBlank() throws Exception {
        when(sttService.available()).thenReturn(true);
        when(sttService.transcribe(any(), any())).thenReturn("환불 정책 알려줘");
        stubAnswer("환불은 ", grounded("환불은 14일 이내 신청.",
                new SourceCitation("faq.md", 1L, "## 환불", 0.9)));

        sendAudio(new byte[]{1, 2, 3, 4});
        utterWithAudio("");   // 브라우저 인식 비어있음(예: Web Speech 차단 환경)

        assertThat(capturedUserText()).isEqualTo("환불 정책 알려줘");
        assertThat(firstEvent("stt.final")).isNotNull();
        assertThat(firstEvent("stt.final").path("text").asText()).isEqualTo("환불 정책 알려줘");
    }

    @Test
    @DisplayName("브라우저·클라우드 모두 비면 빈 발화 → 처리하지 않음(에이전트 미호출)")
    void noTextNoProcessing() throws Exception {
        when(sttService.available()).thenReturn(true);
        when(sttService.transcribe(any(), any())).thenReturn(null);   // 클라우드도 실패

        sendAudio(new byte[]{9});
        utterWithAudio("");   // 브라우저 비어있음

        verify(agentOrchestrator, never()).runStreaming(any(), any(), any(), any());
    }

    @Test
    @DisplayName("STT 비활성 → 브라우저 텍스트 경로(전사 호출 없음)")
    void sttDisabledUsesBrowserText() throws Exception {
        // sttService.available() 기본 false
        stubAnswer("답", grounded("답변입니다.", new SourceCitation("doc.md", 1L, "...", 0.9)));

        utter("브라우저 단독 텍스트");

        assertThat(capturedUserText()).isEqualTo("브라우저 단독 텍스트");
        verify(sttService, never()).transcribe(any(), any());
    }
}
