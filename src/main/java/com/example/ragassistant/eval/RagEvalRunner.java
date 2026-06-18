package com.example.ragassistant.eval;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.service.RagService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 앱 기동 후 평가를 1회 실행하고 JSON/MD를 쓴 뒤 종료한다.
 * 실행 예:
 * gradlew bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
 * 전제:
 * - Ollama + DB 기동
 * - FaqBootstrap 이 FAQ/코퍼스 인덱싱 완료 (@Order로 FAQ 이후 실행)
 */
@Component
@Order(100) // FaqBootstrap(기본 0) 이후
public class RagEvalRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RagEvalRunner.class);
    private final ApplicationArguments args;
    private final EvalQuestionSet questionSet;
    private final RagService ragService;
    private final ChatModelClient ollamaService;
    private final EvalScorer scorer;
    private final EvalReportWriter reportWriter;

    public RagEvalRunner(
            ApplicationArguments args,
            EvalQuestionSet questionSet,
            RagService ragService,
            ChatModelClient ollamaService,
            EvalScorer scorer,
            EvalReportWriter reportWriter
    ) {
        this.args = args;
        this.questionSet = questionSet;
        this.ragService = ragService;
        this.ollamaService = ollamaService;
        this.scorer = scorer;
        this.reportWriter = reportWriter;
    }

    @Override
    public void run(@NonNull ApplicationArguments applicationArguments) throws Exception {
        if (!args.containsOption("rag.eval.enabled")) {
            return; // 평소 bootRun 은 평가 안 함
        }
        EvalMode mode = parseMode(args.getOptionValues("rag.eval.mode"));
        Path questionsPath = Path.of(
                args.getOptionValues("rag.eval.questions") != null
                        ? Objects.requireNonNull(args.getOptionValues("rag.eval.questions")).get(0)
                        : "eval/questions.json"
        );
        List<EvalQuestion> questions = questionSet.load(questionsPath);
        List<EvalResult> results = new ArrayList<>();
        log.info("RAG eval start: mode={}, questions={}", mode, questions.size());
        for (EvalQuestion q : questions) {
            ChatResponse response = runOnce(mode, q.question());
            EvalResult result = scorer.score(q, mode, response);
            results.add(result);
            log.info("#{} score={}/{} grounded={} sources={}",
                    q.id(), result.score(), result.maxScore(),
                    result.grounded(), result.sources().size());
        }
        int total = results.stream().mapToInt(EvalResult::score).sum();
        int max = results.stream().mapToInt(EvalResult::maxScore).sum();
        EvalReport report = new EvalReport("v2", mode, Instant.now(), total, max, results);
        reportWriter.write(report);
        log.info("RAG eval done: {}/{}", total, max);
        // 포폴/CI용: 평가만 하고 끝내려면 System.exit(0) 고려
    }

    private ChatResponse runOnce(EvalMode mode, String question) {
        return switch (mode) {
            case RAG_ON -> ragService.chat(question);
            case RAG_OFF -> {
                // v2: contentPrompt + 질문만 (검색·Context 없음)
                String raw = ollamaService.chat(question);
                yield new ChatResponse(raw, List.of(), false);
            }
        };
    }

    private EvalMode parseMode(List<String> values) {
        if (values == null || values.isEmpty()) return EvalMode.RAG_ON;
        return EvalMode.valueOf(values.get(0).toUpperCase());
    }
}
