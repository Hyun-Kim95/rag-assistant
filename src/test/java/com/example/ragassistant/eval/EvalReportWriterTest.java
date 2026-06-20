package com.example.ragassistant.eval;

import com.example.ragassistant.service.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvalReportWriter 파일 출력 검증.
 *
 * <p>출력: eval/reports/ — latest·compare 는 Git 커밋 대상.
 * runs/ 는 .gitignore 로 로컬 튜닝 이력만 보관.
 */
class EvalReportWriterTest {

    private static final Path BASE_DIR = Path.of("eval/reports");
    private static final Path RUNS_DIR = BASE_DIR.resolve("runs");

    private EvalReportWriter writer;

    @BeforeEach
    void setUp() throws Exception {
        writer = new EvalReportWriter(new ObjectMapper());
        // 이전 테스트·bootRun 산출물이 assert 에 영향 주지 않도록 정리
        if (Files.exists(BASE_DIR)) {
            try (var walk = Files.walk(BASE_DIR)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
    }

    /**
     * RAG_ON 1회: latest-rag-on + runs/ 아래 *_RAG_ON.json|.md 1쌍 생성.
     * runs 파일명 타임스탬프는 ZoneId.systemDefault() 기준이라 OS·타임존에 따라 달라진다.
     */
    @Test
    void write_createsModeLatestAndRunArchive() throws Exception {
        EvalReport report = sampleReport(EvalMode.RAG_ON, Instant.parse("2026-06-17T08:49:18Z"));

        Path latestJson = BASE_DIR.resolve("latest-rag-on.json");
        Path latestMd = BASE_DIR.resolve("latest-rag-on.md");

        writer.write(report);

        assertThat(Files.exists(latestJson)).isTrue();
        assertThat(Files.exists(latestMd)).isTrue();

        // runs/ 파일명은 시스템 타임존(systemDefault) 기준이라 고정 문자열 대신 패턴으로 검증
        try (var runFiles = Files.list(RUNS_DIR)) {
            List<Path> ragOnRuns = runFiles
                    .filter(p -> p.getFileName().toString().endsWith("_RAG_ON.json"))
                    .toList();
            assertThat(ragOnRuns).hasSize(1);
            assertThat(Files.exists(Path.of(ragOnRuns.get(0).toString().replace(".json", ".md")))).isTrue();
        }

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var parsed = mapper.readTree(latestJson.toFile());
        assertThat(parsed.get("totalScore").asInt()).isEqualTo(2);
        assertThat(parsed.get("mode").asText()).isEqualTo("RAG_ON");

        String markdown = Files.readString(latestMd);
        assertThat(markdown).contains("# RAG Eval Report");
        assertThat(markdown).contains("RAG_ON");
        assertThat(markdown).contains(PromptBuilder.NO_ANSWER_MESSAGE);
    }

    @Test
    void write_whenBothModesExist_createsCompareLatest() throws Exception {
        writer.write(sampleReport(EvalMode.RAG_ON, Instant.parse("2026-06-17T08:00:00Z")));
        writer.write(sampleReport(EvalMode.RAG_OFF, Instant.parse("2026-06-17T09:00:00Z")));

        Path compare = BASE_DIR.resolve("compare-latest.md");
        assertThat(Files.exists(compare)).isTrue();

        String text = Files.readString(compare);
        assertThat(text).contains("# RAG Eval Compare");
        assertThat(text).contains("latest-rag-on.json");
    }

    @Test
    void write_ragOff_usesSeparateLatest() throws Exception {
        writer.write(sampleReport(EvalMode.RAG_OFF, Instant.parse("2026-06-17T10:00:00Z")));

        assertThat(Files.exists(BASE_DIR.resolve("latest-rag-off.json"))).isTrue();
        assertThat(Files.exists(BASE_DIR.resolve("latest-rag-on.json"))).isFalse();
        assertThat(Files.exists(BASE_DIR.resolve("compare-latest.md"))).isFalse();
    }

    private static EvalReport sampleReport(EvalMode mode, Instant ranAt) {
        EvalResult one = new EvalResult(
                8, "C_NO_ANSWER", "이 프로젝트의 2025년 매출은?",
                mode, PromptBuilder.NO_ANSWER_MESSAGE,
                false, List.of(), true, 2, 2, List.of()
        );
        return new EvalReport("v2", mode, ranAt, 2, 2, List.of(one), null, 0L);
    }
}
