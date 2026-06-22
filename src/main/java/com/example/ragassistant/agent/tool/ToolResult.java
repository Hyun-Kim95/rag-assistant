package com.example.ragassistant.agent.tool;

import com.example.ragassistant.dto.SourceCitation;

import java.util.List;

/**
 * 도구 실행 결과.
 * - content: 모델에 tool 결과로 되먹일 텍스트.
 * - sources: 응답 출처로 누적할 인용(검색 도구만 채움, 나머지는 빈 리스트).
 */
public record ToolResult(String content, List<SourceCitation> sources) {

    public static ToolResult text(String content) {
        return new ToolResult(content, List.of());
    }
}
