package com.example.ragassistant.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * RAG 평가 리포트 저장.
 *
 * <pre>
 * eval/reports/
 * ├── latest-rag-on.json / .md             ← 모드별 최신 (default 라우팅, Git 커밋 대상)
 * ├── latest-rag-off.json / .md
 * ├── latest-rag-on-{provider}.json/.md    ← provider 강제 실행(예: groq, ollama-7b)
 * ├── compare-latest.md                    ← on·off 둘 다 있을 때
 * ├── compare-providers.md                 ← provider 비교 (≥2개 실행 시)
 * └── runs/                                ← 실행 이력 (.gitignore)
 * </pre>
 */
@Component
public class EvalReportWriter {

    private static final Path BASE_DIR = Path.of("eval/reports");
    private static final Path RUNS_DIR = BASE_DIR.resolve("runs");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ObjectMapper objectMapper;

    public EvalReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void write(EvalReport report) throws IOException {
        Files.createDirectories(BASE_DIR);
        Files.createDirectories(RUNS_DIR);

        String modeSlug = toModeSlug(report.mode());
        String stamp = STAMP.format(report.ranAt());

        if (report.provider() != null) {
            // provider 강제 실행 → suffix 파일. on/off 비교 대상 아님.
            String pSlug = toProviderSlug(report.provider());
            writePair(BASE_DIR.resolve("latest-" + modeSlug + "-" + pSlug), report);
            writePair(RUNS_DIR.resolve(stamp + "_" + report.mode() + "_" + pSlug), report);
        } else {
            writePair(BASE_DIR.resolve("latest-" + modeSlug), report);
            writePair(RUNS_DIR.resolve(stamp + "_" + report.mode()), report);
            maybeWriteCompareLatest();
        }
    }

    /**
     * provider별 리포트 ≥2개를 한 표로 비교.
     */
    public void writeProviderCompare(List<EvalReport> reports) throws IOException {
        if (reports == null || reports.size() < 2) {
            return;
        }
        Files.createDirectories(BASE_DIR);
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG Eval Compare (providers)\n\n");
        sb.append("| provider | mode | total | avgLatencyMs | grounded |\n");
        sb.append("|---|---|---:|---:|---:|\n");
        for (EvalReport r : reports) {
            long grounded = r.results().stream().filter(EvalResult::grounded).count();
            sb.append("| ").append(r.provider())
                    .append(" | ").append(r.mode())
                    .append(" | ").append(r.totalScore()).append("/").append(r.maxTotalScore())
                    .append(" | ").append(r.avgLatencyMs())
                    .append(" | ").append(grounded).append("/").append(r.results().size())
                    .append(" |\n");
        }
        Files.writeString(BASE_DIR.resolve("compare-providers.md"), sb.toString());
    }

    private void writePair(Path basePath, EvalReport report) throws IOException {
        objectMapper.writeValue(new File(basePath + ".json"), report);
        Files.writeString(Path.of(basePath + ".md"), toMarkdown(report));
    }

    private void maybeWriteCompareLatest() throws IOException {
        Optional<EvalReport> on = readIfExists(BASE_DIR.resolve("latest-rag-on.json"));
        Optional<EvalReport> off = readIfExists(BASE_DIR.resolve("latest-rag-off.json"));
        if (on.isEmpty() || off.isEmpty()) {
            return;
        }
        Files.writeString(BASE_DIR.resolve("compare-latest.md"), toCompareMarkdown(on.get(), off.get()));
    }

    private Optional<EvalReport> readIfExists(Path json) {
        if (!Files.exists(json)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json.toFile(), EvalReport.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static String toModeSlug(EvalMode mode) {
        return mode.name().toLowerCase().replace('_', '-'); // RAG_ON → rag-on
    }

    private static String toProviderSlug(String provider) {
        return provider.toLowerCase().replaceAll("[^a-z0-9-]", "-");    // groq, ollama-7b
    }

    private String toMarkdown(EvalReport r) {
        String when = DISPLAY.format(r.ranAt());
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG Eval Report\n\n");
        sb.append("| 항목 | 값 |\n|---|---|\n");
        sb.append("| version | ").append(r.version()).append(" |\n");
        sb.append("| mode | ").append(r.mode()).append(" |\n");
        if (r.provider() != null) {
            sb.append("| provider | ").append(r.provider()).append(" |\n");
        }
        sb.append("| ranAt | ").append(when).append(" |\n");
        sb.append("| avgLatencyMs | ").append(r.avgLatencyMs()).append(" |\n");
        sb.append("| total | **").append(r.totalScore()).append(" / ")
                .append(r.maxTotalScore()).append("** |\n\n");

        sb.append("| # | category | score | grounded | sources | noAnswer |\n");
        sb.append("|---:|---|---:|---|---:|---|\n");
        for (EvalResult e : r.results()) {
            sb.append("| ").append(e.id())
                    .append(" | ").append(e.category())
                    .append(" | ").append(e.score()).append("/").append(e.maxScore())
                    .append(" | ").append(e.grounded())
                    .append(" | ").append(e.sources().size())
                    .append(" | ").append(e.noAnswer())
                    .append(" |\n");
        }
        sb.append("\n## 문항별\n");
        for (EvalResult e : r.results()) {
            sb.append("\n### ").append(e.id()).append(". ").append(e.question()).append("\n\n");
            sb.append("**score:** ").append(e.score()).append("/").append(e.maxScore()).append("\n\n");
            if (!e.failReasons().isEmpty()) {
                sb.append("**failReasons:** ").append(String.join(", ", e.failReasons())).append("\n\n");
            }
            sb.append("```\n").append(e.answer()).append("\n```\n");
        }
        return sb.toString();
    }

    private String toCompareMarkdown(EvalReport on, EvalReport off) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RAG Eval Compare (latest)\n\n");
        sb.append("| 항목 | RAG on | RAG off |\n|---|---|---|\n");
        sb.append("| ranAt | ").append(DISPLAY.format(on.ranAt()))
                .append(" | ").append(DISPLAY.format(off.ranAt())).append(" |\n");
        sb.append("| total | **").append(on.totalScore()).append(" / ").append(on.maxTotalScore())
                .append("** | **").append(off.totalScore()).append(" / ").append(off.maxTotalScore())
                .append("** |\n\n");

        sb.append("| # | question | on | off | Δ |\n");
        sb.append("|---:|---|---:|---:|---:|\n");

        on.results().stream()
                .sorted(Comparator.comparingInt(EvalResult::id))
                .forEach(onRow -> {
                    EvalResult offRow = off.results().stream()
                            .filter(r -> r.id() == onRow.id())
                            .findFirst()
                            .orElse(null);
                    int offScore = offRow != null ? offRow.score() : 0;
                    int delta = onRow.score() - offScore;
                    sb.append("| ").append(onRow.id())
                            .append(" | ").append(onRow.question())
                            .append(" | ").append(onRow.score()).append("/").append(onRow.maxScore())
                            .append(" | ").append(offScore).append("/").append(onRow.maxScore())
                            .append(" | ").append(delta >= 0 ? "+" : "").append(delta)
                            .append(" |\n");
                });

        sb.append("\n> on: `latest-rag-on.json` · off: `latest-rag-off.json`\n");
        return sb.toString();
    }
}
