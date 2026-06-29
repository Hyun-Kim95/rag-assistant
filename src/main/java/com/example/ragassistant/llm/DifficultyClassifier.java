package com.example.ragassistant.llm;

import com.example.ragassistant.config.OllamaProperties;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 질문 난이도를 EASY/HARD로 분류한다(routing-strategy=difficulty 전용).
 * - 분류기 모델은 ollama.classifier-model(기본 qwen2.5:3b). 1b는 형식·정확도가 불안정해 분리.
 * - 분류기는 라우팅 leg가 아니라 내부 전용 → ChatModelClient 빈으로 노출하지 않고 직접 구성.
 * - 분류 실패(연결·응답·형식)는 던지지 않고 HARD로 폴백 → 강한 모델로 안전하게.
 */
@Component
public class DifficultyClassifier {

    private static final Logger log = LoggerFactory.getLogger(DifficultyClassifier.class);

    private static final String SYSTEM = """
            You are a difficulty classifier for a document QA assistant.
            Decide if answering the user's question is EASY or HARD.
            - EASY: a single, direct factual lookup (name, number, URL, port, date, short definition), even if technical.
            - HARD: needs comparison, summarization, multi-step reasoning, or is long/ambiguous.
            Reply with EXACTLY one word: EASY or HARD. No other text.""";

    private final OllamaChatClient classifier;

    public DifficultyClassifier(RestClient ollamaRestClient, ObjectMapper objectMapper, OllamaProperties props,
                                QueryTelemetryContext telemetry) {
        this.classifier = new OllamaChatClient(ollamaRestClient, objectMapper,
                props.classifierModel(), "difficulty-classifier", 0.0, telemetry);
    }

    public DifficultyTier classify(String question) {
        try {
            String raw = classifier.complete(SYSTEM, question);
            if (StringUtils.hasText(raw) && raw.toUpperCase().contains("EASY")) {
                log.debug("difficulty=EASY q='{}'", preview(question));
                return DifficultyTier.EASY;
            }
            log.debug("difficulty=HARD q='{}' raw='{}'", preview(question), raw);
            return DifficultyTier.HARD;
        } catch (Exception e) {
            log.warn("난이도 분류 실패 → HARD 폴백: {}", e.toString());
            return DifficultyTier.HARD;
        }
    }

    private static String preview(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}
