package com.example.ragassistant.parser;

import java.nio.charset.StandardCharsets;

/**
 * 파싱 가능 확장자 확인
 * 파일 바이트 -> plain text
 */
public class DocumentParser {

    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }

    public String parse(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
