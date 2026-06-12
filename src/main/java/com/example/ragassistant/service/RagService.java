package com.example.ragassistant.service;

import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.ChatStreamEvent;
import com.example.ragassistant.dto.SourceCitation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.regex.Pattern;

import static com.example.ragassistant.service.PromptBuilder.NO_ANSWER_MESSAGE;

/**
 * RAG 질의응답: retrieve → (no-answer | prompt → chat) → ChatResponse
 */
@Service
public class RagService {

    /** PromptBuilder [Context]/[Question]/[Answer] 라벨·Context 메타 줄 (답변 끝 잔여물) */
    private static final Pattern TRAILING_PROMPT_META_LINE = Pattern.compile(
            "(?i)^\\s*(\\[(?:Context|Question|Answer)]"
                    + "|Context에서|Context에|Context만).*$");

    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public RagService(Retriever retriever, PromptBuilder promptBuilder, OllamaService ollamaService, ObjectMapper objectMapper) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param question 사용자 질문
     * @return 답변 + sources + grounded
     */
    public ChatResponse chat(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문이 비어 있습니다.");
        }
        List<SearchHit> hits = retriever.retrieve(question.trim());
        // min-score 통과 chunk 없음 → LLM 호출 없이 no-answer (환각·비용 방지)
        if (hits.isEmpty()) {
            return ChatResponse.noAnswer(NO_ANSWER_MESSAGE);
        }
        String prompt = promptBuilder.build(hits, question);
        String answer = sanitizeLlmAnswer(ollamaService.chat(prompt));
        boolean grounded = isGrounded(answer);

        List<SourceCitation> sources = grounded
                ? hits.stream().map(SourceCitation::from).toList()
                : List.of();   // no-answer면 출처 비우기

        return new ChatResponse(grounded ? answer : NO_ANSWER_MESSAGE, sources, grounded);
    }

    /**
     * LLM이 프롬프트의 no-answer 규칙을 오해해 붙이는 잔여 태그 제거.
     */
    private String sanitizeLlmAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return answer;
        }
        String normalized = answer.strip()
                .replaceAll("(?i)\\[no-answer]\\s*$", "")
                .strip();
        String stripped = stripTrailingPromptMetaLines(normalized);
        return stripLeadingAnswerLabel(stripped);
    }

    /** LLM이 붙이는 프롬프트 라벨·Context 메타 요약 등 마지막 줄 제거 */
    private String stripTrailingPromptMetaLines(String answer) {
        String result = answer;
        while (StringUtils.hasText(result)) {
            int lastNewline = result.lastIndexOf('\n');
            String lastLine = lastNewline >= 0 ? result.substring(lastNewline + 1) : result;
            if (!TRAILING_PROMPT_META_LINE.matcher(lastLine).matches()) {
                break;
            }
            result = lastNewline >= 0 ? result.substring(0, lastNewline).strip() : "";
        }
        return result;
    }

    /** LLM이 답 앞에 붙이는 [Answer] 라벨 제거 */
    private String stripLeadingAnswerLabel(String answer) {
        return answer.replaceFirst("(?is)^\\s*\\[Answer]\\s*", "").strip();
    }

    /**
     * LLM 답변이 context 기반(grounded)인지 판별.
     * prompt 문구 변경 시 NO_ANSWER_MESSAGE와 함께 유지할 것.
     */
    private boolean isGrounded(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        String normalized = answer.strip();
        if (normalized.equals(NO_ANSWER_MESSAGE)) {
            return false;
        }
        // no-answer로 시작 + 짧은 부연(중국어 괄호 등)만 no-answer로 간주
        if (normalized.startsWith(NO_ANSWER_MESSAGE)) {
            return normalized.length() > NO_ANSWER_MESSAGE.length() + 40;
        }
        return true;
    }

    /**
     * RAG 스트리밍: retrieve(동기) → LLM stream → SSE done.
     * 호출 스레드에서 blocking — Controller가 별도 스레드에서 실행할 것.
     */
    public void chatStream(String question, SseEmitter emitter) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("질문이 비어 있습니다.");
        }
        try {
            List<SearchHit> hits = retriever.retrieve(question.trim());
            if (hits.isEmpty()) {
                sendDone(emitter, ChatResponse.noAnswer(NO_ANSWER_MESSAGE));
                emitter.complete();
                return;
            }
            String prompt = promptBuilder.build(hits, question);
            String fullAnswer = sanitizeLlmAnswer(ollamaService.streamChat(prompt, piece ->
                    sendEvent(emitter, "delta", ChatStreamEvent.delta(piece))
            ));
            boolean grounded = isGrounded(fullAnswer);
            ChatResponse response = grounded
                    ? new ChatResponse(fullAnswer, hits.stream().map(SourceCitation::from).toList(), true)
                    : ChatResponse.noAnswer(NO_ANSWER_MESSAGE);
            sendDone(emitter, response);
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }
    private void sendEvent(SseEmitter emitter, String eventName, ChatStreamEvent payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(payload)));
        } catch (Exception ex) {
            throw new RuntimeException("SSE 전송 실패", ex);
        }
    }
    private void sendDone(SseEmitter emitter, ChatResponse response) {
        sendEvent(emitter, "done", ChatStreamEvent.done(response));
    }
}
