package com.example.ragassistant.llm.agent;

import java.util.Map;

/**
 * 모델에게 노출하는 도구 1개의 스펙(function calling용).
 * - parameters: JSON Schema(object) 맵. 예: {"type":"object","properties":{...},"required":[...]}.
 * OpenAI·Ollama 둘 다 {type:"function", function:{name, description, parameters}} 형태로 감싼다.
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
