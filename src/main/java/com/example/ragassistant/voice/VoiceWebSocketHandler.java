package com.example.ragassistant.voice;

import com.example.ragassistant.agent.AgentOrchestrator;
import com.example.ragassistant.agent.AgentStreamHandler;
import com.example.ragassistant.domain.CallTurn;
import com.example.ragassistant.dto.AgentResponse;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.ConversationTurn;
import com.example.ragassistant.repository.CallLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 음성 통화 WebSocket 핸들러: 브라우저 STT/TTS, 서버는 텍스트→tool calling agent(멀티턴) 중계.
 * 수신(JSON): {type:"user_utterance", text:"...", sttMs:n} | {type:"barge_in"}
 * 송신: VoiceEvent (state / answer.delta / answer.done / handoff / error) + 오디오 바이너리
 * 부가: 세션 단위 대화 메모리(멀티턴) + 통화 세션·턴 로그(PII 마스킹) + 구간 지연 기록.
 */
@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);
    private static final String ATTR_NO_ANSWER_STREAK = "noAnswerStreak";
    private static final String ATTR_SESSION_ID = "callSessionId";
    private static final String ATTR_TURN_INDEX = "turnIndex";
    private static final String ATTR_FINAL_STATE = "finalState";
    private static final String ATTR_HANDOFF_REASON = "handoffReason";
    private static final String ATTR_HISTORY = "history";
    private static final int HANDOFF_NO_ANSWER_THRESHOLD = 2;

    private final AgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final GoogleTtsService googleTtsService;
    private final CallLogRepository callLogRepository;
    private final PiiMasker piiMasker;

    // agentStreamExecutor(cached pool) 재사용 — 통화 턴 처리를 수신 스레드와 분리.
    public VoiceWebSocketHandler(AgentOrchestrator agentOrchestrator, ObjectMapper objectMapper,
                                 @Qualifier("agentStreamExecutor") ExecutorService executor,
                                 GoogleTtsService googleTtsService,
                                 CallLogRepository callLogRepository, PiiMasker piiMasker) {
        this.agentOrchestrator = agentOrchestrator;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.googleTtsService = googleTtsService;
        this.callLogRepository = callLogRepository;
        this.piiMasker = piiMasker;
    }

    private static long msSince(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(ATTR_NO_ANSWER_STREAK, 0);
        session.getAttributes().put(ATTR_TURN_INDEX, 0);
        session.getAttributes().put(ATTR_HISTORY, new ArrayList<ConversationTurn>());
        try {
            Long sessionId = callLogRepository.createSession(LocalDateTime.now());
            session.getAttributes().put(ATTR_SESSION_ID, sessionId);
        } catch (Exception e) {
            // 로그 저장 실패가 통화를 막지 않도록 흡수(이후 턴 기록은 sessionId 없으면 skip).
            log.warn("call session 생성 실패 → 로그 없이 통화 진행: {}", e.getMessage());
        }
        send(session, VoiceEvent.state(CallState.LISTENING));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            switch (type) {
                case "user_utterance" -> onUtterance(session, node.path("text").asText(""),
                        node.path("sttMs").asInt(0));
                // barge-in: 클라이언트가 TTS를 멈추고 서버는 LISTENING으로 복귀.
                // (진행 중 LLM 스트림의 서버측 취소 전파는 확장 — 현재는 클라이언트 중단으로 흡수)
                case "barge_in" -> send(session, VoiceEvent.state(CallState.LISTENING));
                default -> log.debug("unknown voice message type: {}", type);
            }
        } catch (IOException e) {
            send(session, VoiceEvent.error("BAD_MESSAGE", "메시지 형식이 올바르지 않습니다."));
        }
    }

    // 인사·스몰토크 키워드 (짧은 발화에서만 매칭 → 실제 질문 안의 "안녕" 오탐 방지)
    private static final List<String> GREETING_KEYWORDS = List.of("여보세요", "안녕", "들리", "거기누구", "hello", "hi");

    private void onUtterance(WebSocketSession session, String text, int sttMs) {
        if (text == null || text.isBlank()) {
            return;
        }
        send(session, VoiceEvent.state(CallState.THINKING));
        final long t0 = System.nanoTime();

        String canned = cannedReply(text);
        if (canned != null) {
            ChatResponse response = new ChatResponse(canned, List.of(), false);
            long ttsMs = speakResponse(session, response);
            // 인사는 즉답 → ttfb ≈ 전체 처리 시간, llm 없음(0).
            recordTurn(session, text, response, sttMs, 0, ttsMs, msSince(t0));
            send(session, VoiceEvent.state(CallState.SPEAKING));
            return;
        }

        executor.submit(() -> {
            try {
                final long llmStart = System.nanoTime();
                final long[] firstDeltaNanos = {0L};
                ChatResponse response = answerWithAgent(session, text, firstDeltaNanos);
                long llmMs = msSince(llmStart);
                long ttfbMs = firstDeltaNanos[0] > 0L
                        ? (firstDeltaNanos[0] - t0) / 1_000_000L
                        : llmMs;
                long ttsMs = speakResponse(session, response);
                recordTurn(session, text, response, sttMs, llmMs, ttsMs, ttfbMs);

                if (registerAndCheckHandoff(session, response.grounded())) {
                    String reason = "NO_ANSWER_X" + HANDOFF_NO_ANSWER_THRESHOLD;
                    session.getAttributes().put(ATTR_FINAL_STATE, "HANDOFF");
                    session.getAttributes().put(ATTR_HANDOFF_REASON, reason);
                    send(session, VoiceEvent.handoff(reason));
                    send(session, VoiceEvent.state(CallState.HANDOFF));
                } else {
                    send(session, VoiceEvent.state(CallState.SPEAKING));
                }
            } catch (Exception ex) {
                log.warn("voice turn failed", ex);
                session.getAttributes().put(ATTR_FINAL_STATE, "ERROR");
                send(session, VoiceEvent.error("VOICE_TURN_FAILED", "응답 생성 중 오류가 발생했습니다."));
                send(session, VoiceEvent.state(CallState.LISTENING));
            }
        });
    }

    /**
     * tool calling agent(멀티턴) 호출: 세션 대화 이력을 함께 넘겨 후속 질문도 맥락을 잇는다.
     * delta는 자막으로 흘리고 결과를 ChatResponse로 변환한다. 턴 종료 후 이력에 user/assistant를 누적한다.
     * (도구 호출 step은 음성에서 노출하지 않고, 자막은 최종 답 delta만 흘린다.)
     */
    @SuppressWarnings("unchecked")
    private ChatResponse answerWithAgent(WebSocketSession session, String text, long[] firstDeltaNanos) {
        List<ConversationTurn> history = (List<ConversationTurn>) session.getAttributes()
                .computeIfAbsent(ATTR_HISTORY, k -> new ArrayList<ConversationTurn>());
        final boolean[] noticed = {false};      // 턴당 1회만 안내(filler)
        AgentStreamHandler handler = new AgentStreamHandler() {
            @Override
            public void onToolCall(int index, String tool, Map<String, Object> arguments) {
                // 문서를 실제로 뒤지는 도구를 처음 호출할 때, 본 답변 전에 짧게 안내한다.
                // (list_documents는 즉답성이라 제외) → "찾아볼게요" 후 답하는 자연스러움 + 지연 체감 완화.
                if (!noticed[0] && tool != null
                        && (tool.contains("search") || tool.contains("read") || tool.contains("summarize"))) {
                    noticed[0] = true;
                    speakNotice(session);
                }
            }

            @Override
            public void onToolResult(int index, String tool, String resultSummary) {
            }

            @Override
            public void onDelta(String piece) {
                if (firstDeltaNanos[0] == 0L) {
                    firstDeltaNanos[0] = System.nanoTime();
                }
                send(session, VoiceEvent.delta(piece));
            }
        };
        AgentResponse agentResponse = agentOrchestrator.runStreaming(text, null, history, handler);
        // no-answer(grounded=false) 턴은 이력에 남기지 않는다.
        // 약한 모델이 직전 "찾을 수 없습니다" 답에 앵커링해 다음 질문까지 no-answer로 끌고 가는 오염을 막는다.
        if (agentResponse.grounded()) {
            history.add(new ConversationTurn("user", text));
            history.add(new ConversationTurn("assistant", agentResponse.answer()));
        }
        return new ChatResponse(agentResponse.answer(), agentResponse.sources(),
                agentResponse.grounded(), agentResponse.provider());
    }

    private static final String FILLER_TEXT = "네, 문서에서 찾아볼게요. 잠시만요.";

    /**
     * 검색 시작 안내(filler): 자막 + 본 답변과 같은 음색(Google TTS)으로 먼저 들려준다.
     * Google 비활성/실패 시에만 브라우저 TTS로 강등(이 경우 본 답변도 브라우저 TTS라 음색은 일관).
     */
    private void speakNotice(WebSocketSession session) {
        send(session, VoiceEvent.notice(FILLER_TEXT));
        byte[] audio = googleTtsService.synthesize(FILLER_TEXT);
        if (audio != null) {
            sendAudio(session, audio);
        } else {
            send(session, VoiceEvent.ttsFallback(FILLER_TEXT));
        }
    }

    /**
     * done 전송 후 TTS: Google 성공 시 오디오 바이너리, 실패/비활성 시 브라우저 폴백 지시.
     *
     * @return TTS 합성 소요(ms)
     */
    private long speakResponse(WebSocketSession session, ChatResponse response) {
        send(session, VoiceEvent.done(response));
        long ttsStart = System.nanoTime();
        byte[] audio = googleTtsService.synthesize(response.answer());
        long ttsMs = msSince(ttsStart);
        if (audio != null) {
            sendAudio(session, audio);
        } else {
            send(session, VoiceEvent.ttsFallback(response.answer()));
        }
        return ttsMs;
    }

    /**
     * 턴 로그 저장: user 텍스트는 PII 마스킹 후 기록. 실패해도 통화는 계속.
     */
    private void recordTurn(WebSocketSession session, String userText, ChatResponse response,
                            int sttMs, long llmMs, long ttsMs, long ttfbMs) {
        Object sessionId = session.getAttributes().get(ATTR_SESSION_ID);
        if (!(sessionId instanceof Long sid)) {
            return;     // 세션 생성 실패 시 턴 기록 skip
        }
        int turnIndex = ((Number) session.getAttributes().getOrDefault(ATTR_TURN_INDEX, 0)).intValue();
        session.getAttributes().put(ATTR_TURN_INDEX, turnIndex + 1);
        try {
            callLogRepository.saveTurn(new CallTurn(sid, turnIndex,
                    piiMasker.mask(userText), response.answer(), response.grounded(),
                    sttMs, (int) llmMs, (int) ttsMs, (int) ttfbMs));
        } catch (Exception e) {
            log.warn("call turn 저장 실패: {}", e.getMessage());
        }
    }

    /**
     * 오디오(MP3) 바이너리 프레임 송신. 세션 단위 직렬화.
     */
    private void sendAudio(WebSocketSession session, byte[] audio) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new BinaryMessage(audio));
                }
            }
        } catch (IOException e) {
            log.warn("voice audio send failed", e);
        }
    }

    /**
     * 인사·스몰토크면 정해진 응대 문구, 아니면 null(→ RAG 진행).
     */
    private String cannedReply(String text) {
        String norm = text.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
        if (norm.length() <= 12) {
            for (String k : GREETING_KEYWORDS) {
                if (norm.contains(k)) {
                    return "안녕하세요, 무엇을 도와드릴까요? 문서 내용을 물어보세요.";
                }
            }
        }
        return null;
    }

    /**
     * no-answer 연속 횟수 누적 후 handoff 임계 도달 여부 반환. grounded면 streak 리셋.
     */
    private boolean registerAndCheckHandoff(WebSocketSession session, boolean grounded) {
        int streak = grounded ? 0
                : ((Integer) session.getAttributes().getOrDefault(ATTR_NO_ANSWER_STREAK, 0)) + 1;
        session.getAttributes().put(ATTR_NO_ANSWER_STREAK, streak);
        return streak >= HANDOFF_NO_ANSWER_THRESHOLD;
    }

    /**
     * WebSocketSession.sendMessage는 동시 호출에 안전하지 않으므로 세션 단위로 직렬화한다.
     */
    private void send(WebSocketSession session, VoiceEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("voice send failed", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object sessionId = session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId instanceof Long sid) {
            String finalState = (String) session.getAttributes().getOrDefault(ATTR_FINAL_STATE, "COMPLETED");
            String handoffReason = (String) session.getAttributes().get(ATTR_HANDOFF_REASON);
            try {
                callLogRepository.endSession(sid, LocalDateTime.now(), finalState, handoffReason);
            } catch (Exception e) {
                log.warn("call session 종료 기록 실패: {}", e.getMessage());
            }
        }
        log.debug("voice session closed: {} ({})", session.getId(), status);
    }
}
