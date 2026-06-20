package com.example.ragassistant.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RagService.sanitizeLlmAnswer 후처리 회귀 테스트.
 * qwen2.5:7b이 한자를 흘린 뒤 한국어로 재작성하는 누출 패턴을 정제하는지 확인
 */
class RagServiceSanitizeTest {

    @Test
    void keepsPrimaryAnswerAndDropsChineseLeakAndRewriteFence() {
        // qwen2.5 가 1차 답(영문 용어) 뒤에 중국어 메타 + ```korean 번역 재작성을 붙이는 누출.
        // 번역본은 고유명사를 한국어로 바꿔 키워드를 잃으므로, 펜스 밖 1차 답을 유지해야 한다.
        String raw = """
                - Spring Boot
                - Ollama
                - PostgreSQL pgvector

                문서에 따르면, 이 프로젝트는上述内容是中文，需要翻译成韩语并按照指令格式回答。以下是翻译后的答案：

                ```korean
                - 스프링 부트
                - 올라마
                - PostgreSQL pgvector
                ```
                """;

        String out = RagService.sanitizeLlmAnswer(raw);

        assertThat(out)
                .doesNotContain("上述", "翻译", "需要", "```", "스프링 부트", "올라마")
                .contains("Spring Boot")
                .contains("Ollama")
                .contains("PostgreSQL pgvector");
    }

    @Test
    void usesFenceInnerWhenNoOutsideContent() {
        String raw = """
                上述内容是中文。

                ```korean
                chunk-size는 450 입니다.
                ```
                """;

        String out = RagService.sanitizeLlmAnswer(raw);

        assertThat(out).isEqualTo("chunk-size는 450 입니다.");
    }

    @Test
    void stripsStrayChineseLineWithoutFence() {
        String raw = "이 프로젝트는 Spring Boot 를 씁니다.\n上述内容是中文。";

        String out = RagService.sanitizeLlmAnswer(raw);

        assertThat(out).isEqualTo("이 프로젝트는 Spring Boot 를 씁니다.");
    }

    @Test
    void keepsCleanKoreanAnswerUnchanged() {
        String raw = "chunk-size는 450, chunk-overlap는 150입니다.";

        assertThat(RagService.sanitizeLlmAnswer(raw)).isEqualTo(raw);
    }

    @Test
    void keepsNonLanguageCodeFence() {
        String raw = "설정 예시는 다음과 같습니다.\n```yaml\nport: 8080\n```";

        String out = RagService.sanitizeLlmAnswer(raw);

        assertThat(out)
                .contains("```yaml")
                .contains("port: 8080");
    }

    @Test
    void keepsExistingTrailingMetaAndLabelStrip() {
        String raw = "[Answer]\nchunk-size는 450 입니다.\n[Context]에서 발췌";

        String out = RagService.sanitizeLlmAnswer(raw);

        assertThat(out).isEqualTo("chunk-size는 450 입니다.");
    }
}
