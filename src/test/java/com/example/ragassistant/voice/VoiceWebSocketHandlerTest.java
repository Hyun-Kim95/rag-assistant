package com.example.ragassistant.voice;

import com.example.ragassistant.domain.CallTurn;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.repository.CallLogRepository;
import com.example.ragassistant.service.RagService;
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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * VoiceWebSocketHandler 수용 기준 검증 (DB·LLM·브라우저 없이 Mockito로 결정적).
 * RagService.streamAnswer를 스크립트해 delta·최종 ChatResponse를 흉내내고,
 * WebSocketSession 송신을 캡처해 VoiceEvent(JSON)를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class VoiceWebSocketHandlerTest {

    @Mock
    RagService ragService;
    @Mock
    WebSocketSession session;
    @Mock
    GoogleTtsService googleTtsService; // 기본 synthesize=null → 핸들러가 tts.fallback 경로
    @Mock
    CallLogRepository callLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PiiMasker piiMasker = new PiiMasker(); // 실제 마스킹 검증
    private VoiceWebSocketHandler handler;
    private Map<String, Object> attributes;
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
        handler = new VoiceWebSocketHandler(ragService, objectMapper, syncExecutor, googleTtsService,
                callLogRepository, piiMasker);

        attributes = new HashMap<>();
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

    /**
     * streamAnswer 스텁: delta(있으면) 방출 후 주어진 ChatResponse 반환
     */
    private void stubAnswer(String delta, ChatResponse response) {
        when(ragService.streamAnswer(any(), any())).thenAnswer(inv -> {
            Consumer<String> onDelta = inv.getArgument(1);
            if (delta != null) onDelta.accept(delta);
            return response;
        });
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

    // --- RAG 연결 ---
    @Test
    @DisplayName("user_utterance → answer.delta 수신 + answer.done에 grounded·sources 포함")
    void ragConnected() throws Exception {
        stubAnswer("환불은 ",
                new ChatResponse("환불은 14일 이내 신청.",
                        List.of(new SourceCitation("faq.md", 837L, "## 환불 정책", 0.99)), true));

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
        stubAnswer("답변",
                new ChatResponse("답변입니다.",
                        List.of(new SourceCitation("doc.md", 1L, "...", 0.9)), true));

        utter("질문");

        // 순서: THINKING → (delta/done) → SPEAKING
        assertThat(states()).containsSubsequence("THINKING", "SPEAKING");
    }

    // --- no-answer 상태 ---
    @Test
    @DisplayName("검색 hit 없음 → answer.done grounded=false, 1회차는 handoff 아님(통화 계속)")
    void noAnswerKeepsCall() throws Exception {
        stubAnswer(null, ChatResponse.noAnswer("문서에서 확인할 수 없는 질문입니다."));

        utter("2025년 매출 알려줘");

        JsonNode done = firstEvent("answer.done");
        assertThat(done).isNotNull();
        assertThat(done.path("grounded").asBoolean()).isFalse();
        // 1회 no-answer는 전환 임계 미만 → handoff 없음, SPEAKING으로 계속
        assertThat(firstEvent("handoff")).isNull();
        assertThat(states()).contains("SPEAKING");
    }

    // --- 마스킹 저장 ---
    @Test
    @DisplayName("턴 종료 시 user_text_masked에 PII(전화번호)가 마스킹되어 저장")
    void maskedPersistence() throws Exception {
        stubAnswer(null, ChatResponse.noAnswer("문서에서 확인할 수 없는 질문입니다."));

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
        stubAnswer("부분", new ChatResponse("답변입니다.",
                List.of(new SourceCitation("doc.md", 1L, "...", 0.9)), true));

        utter("질문 내용입니다", 120);

        ArgumentCaptor<CallTurn> captor = ArgumentCaptor.forClass(CallTurn.class);
        verify(callLogRepository).saveTurn(captor.capture());
        CallTurn turn = captor.getValue();
        assertThat(turn.sttMs()).isEqualTo(120);
        assertThat(turn.llmMs()).isGreaterThanOrEqualTo(0);
        assertThat(turn.ttsMs()).isGreaterThanOrEqualTo(0);
        assertThat(turn.ttfbMs()).isGreaterThanOrEqualTo(0);
    }
}
