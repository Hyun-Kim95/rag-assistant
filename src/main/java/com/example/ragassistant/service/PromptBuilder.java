package com.example.ragassistant.service;

import com.example.ragassistant.domain.SearchHit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 검색된 context와 사용자 질문으로 LLM prompt 조립.
 * 환각 줄이기: context 밖 추측 금지, 출처 언급 유도.
 */
@Component
public class PromptBuilder {

    public static final String NO_ANSWER_MESSAGE = "문서에서 확인할 수 없는 질문입니다.";

    /**
     * @param hits     min-score 통과한 검색 결과
     * @param question 사용자 질문
     */
    public String build(List<SearchHit> hits, String question) {
        String context = hits.stream()
                .map(this::formatHit)
                .collect(Collectors.joining("\n\n"));
        return """
                당신은 문서 기반 Q&A 어시스턴트입니다.
                아래 [Context]만 근거로 답하세요. 추측하지 마세요.
                규칙:
                - 반드시 한국어로만 답하세요. 중국어·영어 문장을 붙이지 마세요.
                - 기술 스택·목록·나열 질문이면 Context에 있는 관련 항목을 빠짐없이 모두 나열하세요. 일부만 골라 답하지 마세요.
                - Context에 명시되지 않은 기술은 기술 스택 답에 넣지 마세요.
                  Context에 없는 항목은 나열하지 마세요.
                - 체크리스트·작업 목록과 기술 스택을 혼동하지 마세요.
                - Context에 답이 있으면 그 내용을 설명하세요. no-answer 문구는
                  Context에 해당 정보가 없을 때만 단독으로 출력하세요.
                - [no-answer] 같은 태그·메타 표시는 출력하지 마세요.
                - [Context], [Question], [Answer] 라벨을 답변에 쓰지 마세요.
                - "Context에서", "위와 같습니다", "다음과 같습니다" 같은 메타 요약으로
                  끝내지 마세요. 질문에 대한 내용만 바로 답하세요.
                - Context에 답이 없으면 아래 문장만 출력하세요. 다른 말을 덧붙이지 마세요.
                  %s
                - 가능하면 본문 안에 근거 문서명을 자연스럽게 넣으세요.
                [Context]
                %s
                [Question]
                %s
                [Answer]
                """.formatted(
                        NO_ANSWER_MESSAGE,
                        context,
                        question.trim()
                );
    }

    /**
     * context 블록 1건: 문서명 + 본문
     */
    private String formatHit(SearchHit hit) {
        return """
                --- Source: %s (chunkId=%d, score=%.3f) ---
                %s
                """.formatted(
                hit.getDocumentName(),
                hit.getChunkId(),
                hit.getScore(),
                hit.getContent()
        );
    }
}
