package com.example.ragassistant.eval;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.service.PromptBuilder;
import com.example.ragassistant.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RagEvalRunner 오케스트레이션 검증.
 * Ollama·DB·Spring Boot 전체 기동 없이 Mockito로 분기만 확인한다.
 *
 * <p>평소 bootRun: --rag.eval.enabled 없음 → Runner 즉시 return.
 * 평가 실행: --rag.eval.enabled=true --rag.eval.mode=RAG_ON|RAG_OFF
 */
@ExtendWith(MockitoExtension.class)
class RagEvalRunnerTest {

    @Mock
    private RagService ragService;

    @Mock
    private ChatModelClient ollamaService;

    /** 파일 I/O는 이 테스트에서 검증하지 않음 — EvalReportWriterTest 담당 */
    @Mock
    private EvalReportWriter reportWriter;

    @TempDir
    Path tempDir;

    private EvalQuestionSet questionSet;
    private EvalScorer scorer;
    private Path questionsFile;

    @BeforeEach
    void setUp() throws Exception {
        questionSet = new EvalQuestionSet(new ObjectMapper());
        scorer = new EvalScorer();
        // 8번 no-answer 1문항만 — Runner 루프·채점·리포트 전달 경로만 검증
        questionsFile = tempDir.resolve("questions.json");
        Files.writeString(questionsFile, """
                {
                  "version": "test",
                  "description": "runner unit test",
                  "questions": [
                    {
                      "id": 8,
                      "category": "C_NO_ANSWER",
                      "question": "이 프로젝트의 2025년 매출은?",
                      "maxScore": 2,
                      "expectNoAnswer": true,
                      "mustGrounded": false,
                      "maxSourceCount": 0,
                      "mustContainAll": [],
                      "mustContainAny": [],
                      "mustNotContain": []
                    }
                  ]
                }
                """);
    }

    /**
     * CLI에 --rag.eval.enabled 가 없으면 평가 파이프라인 전체 스킵.
     * 일반 bootRun / UI 사용 시 이 경로가 기본이다.
     */
    @Test
    void run_whenEvalDisabled_skipsExecution() throws Exception {
        var args = new DefaultApplicationArguments();
        var runner = new RagEvalRunner(args, questionSet, ragService, ollamaService, scorer, reportWriter);

        runner.run(args);

        verify(ragService, never()).chat(any());
        verify(reportWriter, never()).write(any());
    }

    /**
     * RAG_ON: RagService.chat → EvalScorer → reportWriter.write.
     * mock이 no-answer를 반환하므로 8번 만점(2/2) 기대.
     */
    @Test
    void run_whenRagOnEnabled_executesAndWritesReport() throws Exception {
        var args = new DefaultApplicationArguments(
                "--rag.eval.enabled=true",
                "--rag.eval.mode=RAG_ON",
                "--rag.eval.questions=" + questionsFile
        );
        when(ragService.chat("이 프로젝트의 2025년 매출은?"))
                .thenReturn(ChatResponse.noAnswer(PromptBuilder.NO_ANSWER_MESSAGE));

        var runner = new RagEvalRunner(args, questionSet, ragService, ollamaService, scorer, reportWriter);
        runner.run(args);

        verify(ragService).chat("이 프로젝트의 2025년 매출은?");
        verify(ollamaService, never()).chat(any());

        ArgumentCaptor<EvalReport> captor = ArgumentCaptor.forClass(EvalReport.class);
        verify(reportWriter).write(captor.capture());

        EvalReport report = captor.getValue();
        assertThat(report.mode()).isEqualTo(EvalMode.RAG_ON);
        assertThat(report.totalScore()).isEqualTo(2);
        assertThat(report.maxTotalScore()).isEqualTo(2);
        assertThat(report.results()).hasSize(1);
        assertThat(report.results().get(0).noAnswer()).isTrue();
    }

    /**
     * RAG_OFF: 검색 없이 OllamaService.chat만 호출 (v2 ablation).
     * 일반 LLM 답변은 no-answer 규칙을 만족하지 않아 0점 기대.
     */
    @Test
    void run_whenRagOffEnabled_callsOllamaOnly() throws Exception {
        var args = new DefaultApplicationArguments(
                "--rag.eval.enabled=true",
                "--rag.eval.mode=RAG_OFF",
                "--rag.eval.questions=" + questionsFile
        );
        when(ollamaService.chat("이 프로젝트의 2025년 매출은?"))
                .thenReturn("일반 LLM 답변입니다.");

        var runner = new RagEvalRunner(args, questionSet, ragService, ollamaService, scorer, reportWriter);
        runner.run(args);

        verify(ollamaService).chat(eq("이 프로젝트의 2025년 매출은?"));
        verify(ragService, never()).chat(any());

        ArgumentCaptor<EvalReport> captor = ArgumentCaptor.forClass(EvalReport.class);
        verify(reportWriter).write(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(EvalMode.RAG_OFF);
        assertThat(captor.getValue().totalScore()).isZero();
    }
}
