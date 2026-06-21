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
        // provider별 비교 실행. 한 leg가 실패해도 나머지는 끝까지 진행(예: 로컬 모델 timeout이 SaaS 비교를 막지 않도록).
        List<EvalReport> reports = new ArrayList<>();
        for (String provider : providers) {
            try {
                reports.add(runAndWrite(mode, questions, provider));
            } catch (Exception ex) {
                log.error("provider={} eval 실패, 다음 provider로 진행: {}", provider, ex.toString());
            }
        }
        if (reports.size() >= 2) {
            reportWriter.writeProviderCompare(reports);
        }
        log.info("RAG eval provider compare done: requested={} succeeded={}", providers, reports.size());
    }

    private EvalReport runAndWrite(EvalMode mode, List<EvalQuestion> questions, String provider) throws IOException {
        // "router" 토큰 = provider 미지정(null) → 설정된 routing-strategy(fixed|difficulty)로 라우팅.
        String callProvider = "router".equalsIgnoreCase(provider) ? null : provider;
        List<EvalResult> results = new ArrayList<>();
        long totalLatencyMs = 0;
        log.info("RAG eval start: mode={}, provider={}, questions={}",
                mode, provider == null ? "(default)" : provider, questions.size());
        long delayMs = parseDelayMs(args.getOptionValues("rag.eval.delay-ms"));
        List<String> throttleProviders = parseProviders(args.getOptionValues("rag.eval.throttle-providers"));
        boolean throttleThis = delayMs > 0 && shouldThrottle(provider, throttleProviders);
        boolean firstQuestion = true;
        for (EvalQuestion q : questions) {
            if (!firstQuestion && throttleThis) {
                throttle(delayMs);  // 지정 provider(예: groq)에만 적용. 측정 구간(t0) 밖이라 latency 미포함.
            }
            firstQuestion = false;
            long t0 = System.nanoTime();
            EvalResult result;
            long latencyMs;
            try {
                ChatResponse response = runOnce(mode, q.question(), callProvider);
                latencyMs = (System.nanoTime() - t0) / 1_000_000L;
                result = scorer.score(q, mode, response);
            } catch (RuntimeException ex) {
                // 문항 1건 실패(예: provider timeout)는 0점으로 기록하고 다음 문항 진행 → 전체 실행이 죽지 않음.
                latencyMs = (System.nanoTime() - t0) / 1_000_000L;
                result = failedResult(q, mode, ex.toString());
                log.warn("#{} eval 실패(0점 처리) latencyMs={}: {}", q.id(), latencyMs, ex.toString());
            }
            totalLatencyMs += latencyMs;
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

    /** 문항 실행이 예외로 실패했을 때 0점 결과로 기록(리포트·compare가 끊기지 않도록). */
    private EvalResult failedResult(EvalQuestion q, EvalMode mode, String error) {
        return new EvalResult(
                q.id(), q.category(), q.question(), mode,
                "[ERROR] " + error, false, List.of(),
                false, 0, q.maxScore(), List.of("error: " + error));
    }

    private boolean shouldThrottle(String provider, List<String> throttleProviders) {
        if (throttleProviders.isEmpty()) {
            return true;    // 지정 없으면 전역 적용(--rag.eval.delay-ms 단독 = 모든 provider)
        }
        return provider != null && throttleProviders.contains(provider);
    }

    private long parseDelayMs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(values.get(0).trim()));
        } catch (NumberFormatException ex) {
            log.warn("rag.eval.delay-ms 파싱 실패, 0 사용: {}", values.get(0));
            return 0L;
        }
    }

    private void throttle(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("eval throttle 대기 중 인터럽트", ex);
        }
    }

    private ChatResponse runOnce(EvalMode mode, String question, String provider) {
        // provider가 지정되면 strict(폴백 없음)로 호출 → 비교표의 각 행이 "순수 그 provider" 측정값이 되도록.
        boolean strict = provider != null;
        return switch (mode) {
            case RAG_ON -> ragService.chat(question, provider, strict);
            case RAG_OFF -> {
                // v2: contentPrompt + 질문만 (검색·Context 없음). provider 지정 시 해당 leg(strict).
                String raw = strict
                        ? ollamaService.chatStrict(question, provider)
                        : ollamaService.chat(question, provider);
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
