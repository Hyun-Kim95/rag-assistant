package com.example.ragassistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * eval/questions.json 파싱 검증.
 * Ollama·DB 없이 실행 — 질문 세트 스키마가 깨지지 않았는지 확인한다.
 */
class EvalQuestionSetTest {

    private EvalQuestionSet questionSet;

    @BeforeEach
    void setUp() {
        questionSet = new EvalQuestionSet(new ObjectMapper());
    }

    /**
     * 프로덕션 SSOT(프로젝트 루트 eval/questions.json) 로드.
     * Gradle test 작업 디렉터리가 프로젝트 루트이므로 RagEvalRunner와 동일 경로를 쓴다.
     */
    @Test
    void load_fromProjectRoot_evalQuestionsJson() throws Exception {
        var questions = questionSet.load(Path.of("eval/questions.json"));

        // v2 고정 10문항
        assertThat(questions).hasSize(10);
        // 1번: 문서 내 사실 (기술 스택)
        assertThat(questions.get(0).id()).isEqualTo(1);
        assertThat(questions.get(0).question()).contains("기술 스택");
        // 10번: 문서 밖 no-answer
        assertThat(questions.get(9).id()).isEqualTo(10);
        assertThat(questions.get(9).expectNoAnswer()).isTrue();
    }

    /**
     * classpath fixture(src/test/resources/eval/questions.json) 로드.
     * IDE·CI에서 프로젝트 루트 파일 없이도 축소 세트로 테스트 가능.
     */
    @Test
    void load_fromClasspath_testFixture() throws Exception {
        Path path = new ClassPathResource("eval/questions.json").getFile().toPath();
        var questions = questionSet.load(path);

        assertThat(questions).hasSize(2);
        assertThat(questions).extracting(EvalQuestion::id).containsExactly(1, 8);
    }

    /**
     * 채점 필드가 JSON → record로 올바르게 매핑되는지 (2번 문항 샘플).
     * mustContainAll·mustGrounded·minSourceCount는 EvalScorer가 그대로 사용한다.
     */
    @Test
    void load_parsesScoringFields() throws Exception {
        var questions = questionSet.load(Path.of("eval/questions.json"));
        EvalQuestion q2 = questions.stream().filter(q -> q.id() == 2).findFirst().orElseThrow();

        assertThat(q2.mustContainAll()).containsExactly("http://localhost:11434");
        assertThat(q2.mustGrounded()).isTrue();
        assertThat(q2.minSourceCount()).isEqualTo(1);
    }
}
