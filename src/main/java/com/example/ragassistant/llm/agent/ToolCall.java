package com.example.ragassistant.llm.agent;

/**
 * 모델이 요청한 도구 호출 1건.
 * - id: tool_call 식별자(OpenAI 제공값, Ollama는 클라이언트가 합성).
 * - name: 호출할 도구 이름.
 * - argumentsJson: 인자(JSON 문자열로 정규화). OpenAI는 원래 문자열, Ollama는 객체 → 문자열로 통일.
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
