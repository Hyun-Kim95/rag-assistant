package com.example.ragassistant.llm.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일부 모델(gpt-oss·qwen 등)이 tool_calls 필드 대신 본문(content)에 도구 호출을
 * '{"name":"...","arguments":{...}}' JSON으로 흘리는 경우를 감지·복구한다.
 * 우리 도구명으로 한정하고 인자는 중첩 없는 평면 객체만 받아 일반 답변 오탐을 막는다.
 */
public final class ToolCallEcho {

    private static final Pattern TOOL_CALL_IN_CONTENT = Pattern.compile(
            "(?s)\\{\\s*\"name\"\\s*:\\s*\"(search_documents|list_documents|read_document|summarize_document)\""
                    + "\\s*(?:,\\s*\"arguments\"\\s*:\\s*(\\{[^{}]*}))?\\s*}");

    private ToolCallEcho() {
    }

    /**
     * content 본문에 도구 호출 JSON 흉내가 들어 있는지.
     */
    public static boolean contains(String content) {
        return content != null && TOOL_CALL_IN_CONTENT.matcher(content).find();
    }

    /**
     * content 본문에 섞인 도구 호출 JSON을 ToolCall 목록으로 복구. 없으면 빈 목록.
     */
    public static List<ToolCall> recover(String content, ObjectMapper objectMapper) {
        List<ToolCall> out = new ArrayList<>();
        if (content == null) {
            return out;
        }
        Matcher m = TOOL_CALL_IN_CONTENT.matcher(content);
        int i = 0;
        while (m.find()) {
            String toolName = m.group(1);
            String argsJson = m.group(2) != null ? m.group(2) : "{}";
            try {
                objectMapper.readTree(argsJson);    // 유효성 확인(깨지면 빈 인자)
            } catch (Exception e) {
                argsJson = "{}";
            }
            out.add(new ToolCall("call_recovered_" + i, toolName, argsJson));
            i++;
        }
        return out;
    }
}
