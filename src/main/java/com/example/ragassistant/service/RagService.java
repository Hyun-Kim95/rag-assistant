package com.example.ragassistant.service;

import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceCitation;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.ragassistant.service.PromptBuilder.NO_ANSWER_MESSAGE;

/**
 * RAG 질의응답: retrieve → (no-answer | prompt → chat) → ChatResponse
 */
@Service
public class RagService {

    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final OllamaService ollamaService;

    public RagService(Retriever retriever, PromptBuilder promptBuilder, OllamaService ollamaService) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.ollamaService = ollamaService;
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
        String answer = ollamaService.chat(prompt);
        boolean grounded = !isNoAnswer(answer);

        List<SourceCitation> sources = grounded
                ? hits.stream().map(SourceCitation::from).toList()
                : List.of();   // no-answer면 출처 비우기

        return new ChatResponse(grounded ? answer : NO_ANSWER_MESSAGE, sources, grounded);
    }

    /**
     * LLM이 no-answer 패턴으로 답했는지 판별.
     * prompt 문구 변경 시 NO_ANSWER_MESSAGE와 함께 유지할 것.
     */
    private boolean isNoAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return true;
        }
        String normalized = answer.strip();
        // 정확히 no-answer 문구만
        if (normalized.equals(NO_ANSWER_MESSAGE)) {
            return true;
        }
        // no-answer로 시작 + 부연 (중국어 괄호 등)
        if (normalized.startsWith(NO_ANSWER_MESSAGE)) {
            return normalized.length() <= NO_ANSWER_MESSAGE.length() + 40;
        }
        return false;
    }
}
