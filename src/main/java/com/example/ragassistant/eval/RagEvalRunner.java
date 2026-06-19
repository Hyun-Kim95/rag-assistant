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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 앱 기동 후 평가를 1회 실행하고 JSON/MD를 쓴 뒤 종료한다.
 * 실행 예:
 *      gradlew bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON"
 *      gradlew bootRun --args="--rag.eval.enabled=true --rag.eval.mode=RAG_ON --rag.eval.providers=ollama-7b,groq"
 * 전제: Ollama + DB 기동, FaqBootstrap 인덱싱 완료(@Order로 FAQ 이후).
 */
@Component
@Order(100)
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

        List<String> providers = parseProviders(args.getOptionValues("rag.eval.providers"));
        if (providers.isEmpty()) {
            runAndWrite(mode, questions, null); // 기존 동작: default 라우팅 단일 실행
            return;
        }
        // provider별 비교 실행
        List<EvalReport> reports = new ArrayList<>();
        for (String provider : providers) {
            reports.add(runAndWrite(mode, questions, provider));
        }
        reportWriter.writeProviderCompare(reports);
        log.info("RAG eval provider compare done: {}", providers);
    }

    private EvalReport runAndWrite(EvalMode mode, List<EvalQuestion> questions, String provider) throws IOException {
        List<EvalResult> results = new ArrayList<>();
        long totalLatencyMs = 0;
        log.info("RAG eval start: mode={}, provider={}, questions={}",
                mode, provider == null ? "(default)" : provider, questions.size());
        for (EvalQuestion q : questions) {
            long t0 = System.nanoTime();
            ChatResponse response = runOnce(mode, q.question(), provider);
            long latencyMs = (System.nanoTime() - t0) / 1_000_000L;
            totalLatencyMs += latencyMs;
            EvalResult result = scorer.score(q, mode, response);
            results.add(result);
            log.info("#{} score={}/{} grounded={} sources={} latencyMs={}",
                    q.id(), result.score(), result.maxScore(),
                    result.grounded(), result.sources().size(), latencyMs);
        }
        int total = results.stream().mapToInt(EvalResult::score).sum();
        int max = results.stream().mapToInt(EvalResult::maxScore).sum();
        long avgLatencyMs = questions.isEmpty() ? 0 : totalLatencyMs / questions.size();
        EvalReport report = new EvalReport("v2", mode, Instant.now(), total, max, results, provider, avgLatencyMs);
        reportWriter.write(report);
        log.info("RAG eval done: provider={} {}/{} avgLatencyMs={}",
                provider == null ? "(default)" : provider, total, max, avgLatencyMs);
        return report;
    }

    private ChatResponse runOnce(EvalMode mode, String question, String provider) {
        return switch (mode) {
            case RAG_ON -> ragService.chat(question, provider);
            case RAG_OFF -> {
                // v2: contentPrompt + 질문만 (검색·Context 없음). provider 지정 시 해당 leg.
                String raw = ollamaService.chat(question, provider);
                yield new ChatResponse(raw, List.of(), false);
            }
        };
    }

    private List<String> parseProviders(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(values.get(0).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private EvalMode parseMode(List<String> values) {
        if (values == null || values.isEmpty()) return EvalMode.RAG_ON;
        return EvalMode.valueOf(values.get(0).toUpperCase());
    }
}
