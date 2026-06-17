package com.example.ragassistant.eval;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.service.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalScorer 룰 기반 채점 검증.
 * LLM·RagService 없이 고정 ChatResponse만 넣어 0/1/2점·failReasons를 확인한다.
 */
class EvalScorerTest {

    private EvalScorer scorer;
    private List<EvalQuestion> questions;

    @BeforeEach
    void setUp() throws Exception {
        scorer = new EvalScorer();
        // 프로덕션과 동일한 10문항 세트
        questions = new EvalQuestionSet(new ObjectMapper())
                .load(Path.of("eval/questions.json"));
    }

    private EvalQuestion byId(int id) {
        return questions.stream().filter(q -> q.id() == id).findFirst().orElseThrow();
    }

    private static SourceCitation sampleSource() {
        return new SourceCitation("DECISIONS.md", 1L, "snippet", 0.72);
    }

    private static List<SourceCitation> oneSource() {
        return List.of(sampleSource());
    }

    // --- C. 문서 밖 no-answer 문항 (8~10) ---

    /**
     * 정상 no-answer: grounded=false, sources=[], 고정 문구 → 만점.
     */
    @Test
    void noAnswerQuestion_correctResponse_scoresMax() {
        EvalQuestion q8 = byId(8);
        ChatResponse response = ChatResponse.noAnswer(PromptBuilder.NO_ANSWER_MESSAGE);

        EvalResult result = scorer.score(q8, EvalMode.RAG_ON, response);

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.noAnswer()).isTrue();
        assertThat(result.grounded()).isFalse();
        assertThat(result.failReasons()).isEmpty();
    }

    /**
     * 문서 밖 질문인데 grounded=true·출처 있음 → 환각으로 0점.
     */
    @Test
    void noAnswerQuestion_groundedTrue_scoresZero() {
        EvalQuestion q8 = byId(8);
        ChatResponse response = new ChatResponse("2025년 매출은 100억입니다.", oneSource(), true);

        EvalResult result = scorer.score(q8, EvalMode.RAG_ON, response);

        assertThat(result.score()).isZero();
        assertThat(result.failReasons()).isNotEmpty();
    }

    /**
     * grounded=false이지만 고정 no-answer 문구가 아님 → 0점 (v2 RAG off 패턴).
     */
    @Test
    void noAnswerQuestion_wrongPhrase_scoresZero() {
        EvalQuestion q8 = byId(8);
        ChatResponse response = new ChatResponse("모르겠습니다. 더 알려주세요.", List.of(), false);

        EvalResult result = scorer.score(q8, EvalMode.RAG_ON, response);

        assertThat(result.score()).isZero();
        assertThat(result.noAnswer()).isFalse();
    }

    // --- A. 문서 내 사실 문항 (1~4) ---

    /**
     * 2번: mustContainAll에 base-url 포함 + grounded + sources → 2점.
     */
    @Test
    void factQuestion_correctBaseUrl_scoresMax() {
        EvalQuestion q2 = byId(2);
        ChatResponse response = new ChatResponse(
                "Ollama base-url은 http://localhost:11434 입니다.",
                oneSource(),
                true
        );

        EvalResult result = scorer.score(q2, EvalMode.RAG_ON, response);

        assertThat(result.score()).isEqualTo(2);
        assertThat(result.failReasons()).isEmpty();
    }

    /**
     * 3번: 두 모델명 모두 포함 → 2점.
     */
    @Test
    void factQuestion_bothModels_scoresMax() {
        EvalQuestion q3 = byId(3);
        ChatResponse response = new ChatResponse(
                "chat은 qwen2.5:7b, embedding은 nomic-embed-text 입니다.",
                oneSource(),
                true
        );

        EvalResult result = scorer.score(q3, EvalMode.RAG_ON, response);

        assertThat(result.score()).isEqualTo(2);
    }

    /**
     * 3번: mustNotContain(ChatGPT 등) 위반 → failReasons + 0점.
     */
    @Test
    void factQuestion_forbiddenKeyword_scoresZero() {
        EvalQuestion q3 = byId(3);
        ChatResponse response = new ChatResponse(
                "ChatGPT와 Claude를 쓸 수 있습니다.",
                oneSource(),
                true
        );

        EvalResult result = scorer.score(q3, EvalMode.RAG_ON, response);

        assertThat(result.score()).isZero();
        assertThat(result.failReasons()).anyMatch(r -> r.startsWith("forbidden:"));
    }

    /**
     * mustGrounded=true인데 grounded=false → 0점.
     */
    @Test
    void factQuestion_notGrounded_scoresZero() {
        EvalQuestion q2 = byId(2);
        ChatResponse response = new ChatResponse(
                "http://localhost:11434",
                List.of(),
                false
        );

        EvalResult result = scorer.score(q2, EvalMode.RAG_ON, response);

        assertThat(result.score()).isZero();
        assertThat(result.failReasons()).contains("mustGrounded but grounded=false");
    }

    /**
     * minSourceCount 미달(sources 빈 배열) → 0점.
     */
    @Test
    void factQuestion_missingSources_scoresZero() {
        EvalQuestion q2 = byId(2);
        ChatResponse response = new ChatResponse(
                "http://localhost:11434",
                List.of(),
                true
        );

        EvalResult result = scorer.score(q2, EvalMode.RAG_ON, response);

        assertThat(result.score()).isZero();
        assertThat(result.failReasons()).contains("sources below minSourceCount");
    }

    /**
     * 1번: mustContainAny 6개 중 절반(3) 미만이면 부분 정확 → 1점.
     */
    @Test
    void factQuestion_partialKeywords_scoresOne() {
        EvalQuestion q1 = byId(1);
        ChatResponse response = new ChatResponse(
                "Spring Boot와 Ollama를 사용합니다.",
                oneSource(),
                true
        );

        EvalResult result = scorer.score(q1, EvalMode.RAG_ON, response);

        assertThat(result.score()).isEqualTo(1);
        assertThat(result.failReasons()).isEmpty();
    }

    // --- B. 문서 내 정책 문항 (5~7) ---

    /**
     * 7번: mustContainAll 450·150 모두 포함 → 2점.
     */
    @Test
    void policyQuestion_chunkSize_scoresMax() {
        EvalQuestion q7 = byId(7);
        ChatResponse response = new ChatResponse(
                "chunk-size는 450, chunk-overlap은 150 입니다.",
                oneSource(),
                true
        );

        EvalResult result = scorer.score(q7, EvalMode.RAG_ON, response);

        assertThat(result.score()).isEqualTo(2);
    }

    /**
     * EvalMode가 결과에 기록되는지 — classpath fixture + RAG_OFF 조합.
     */
    @Test
    void loadsFixtureFromClasspath_forIsolatedCase() throws Exception {
        var fixture = new EvalQuestionSet(new ObjectMapper())
                .load(new ClassPathResource("eval/questions.json").getFile().toPath());
        EvalQuestion q8 = fixture.stream().filter(q -> q.id() == 8).findFirst().orElseThrow();

        EvalResult result = scorer.score(q8, EvalMode.RAG_OFF,
                ChatResponse.noAnswer(PromptBuilder.NO_ANSWER_MESSAGE));

        assertThat(result.mode()).isEqualTo(EvalMode.RAG_OFF);
        assertThat(result.score()).isEqualTo(2);
    }
}
