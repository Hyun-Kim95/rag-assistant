package com.example.ragassistant.voice;

/**
 * TTS 합성 전 마크다운·코드 서식·기호를 듣기 좋게 정제. (voice.js cleanForSpeech와 동일 규칙)
 */
public final class SpeechTextSanitizer {

    private SpeechTextSanitizer() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("```", " ")                        // 코드펜스
                .replace("`", "")                           // 인라인 백틱
                .replaceAll("\\*\\*([^*]*)\\*\\*", "$1")    // **굵게**
                .replaceAll("__([^_]*)__", "$1")            // __굵게__
                .replaceAll("\\[(.*?)]\\(.*?\\)", "$1")     // [텍스트](링크)
                .replaceAll("(?m)^\\s*[-*•]\\s+", "")       // 줄머리 불릿
                .replaceAll("[#>*_~]", " ")                 // 남은 마크다운 기호
                .replaceAll("[/\\\\]", " ")                 // 슬래시·역슬래시
                .replace("@", " ")                          // @Primary
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n{2,}", ". ")
                .trim();
    }
}
