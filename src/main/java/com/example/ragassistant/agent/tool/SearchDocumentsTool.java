package com.example.ragassistant.agent.tool;

import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.service.Retriever;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * search_documents: 업로드 문서에서 질의와 관련된 chunk를 검색한다(기존 Retriever 재사용).
 * 결과 스니펫을 모델에 되먹이고, 같은 hit를 SourceCitation으로 출처에 누적한다.
 */
@Component
public class SearchDocumentsTool implements AgentTool {

    private final Retriever retriever;

    public SearchDocumentsTool(Retriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public String name() {
        return "search_documents";
    }

    @Override
    public String description() {
        return "업로드된 문서에서 질문과 관련된 내용을 검색한다. 문서 내용에 근거해 답하려면 먼저 이 도구를 호출한다. "
                + "query에는 사용자의 질문을 한국어 원문 그대로 넣는다. 단어를 임의로 바꾸거나 번역·축약하지 않는다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "검색어. 사용자 질문 원문을 그대로 사용(임의 변형 금지).")),
                "required", List.of("query"));
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        String query = arguments.path("query").asText("");
        if (!StringUtils.hasText(query)) {
            return ToolResult.text("오류: query 인자가 필요합니다.");
        }
        List<SearchHit> hits = retriever.retrieve(query.trim());
        if (hits.isEmpty()) {
            return ToolResult.text("검색 결과 없음. 관련 문서를 찾지 못했습니다.");
        }
        StringBuilder sb = new StringBuilder("검색 결과 ").append(hits.size()).append("건:\n");
        List<SourceCitation> sources = hits.stream().map(SourceCitation::from).toList();
        for (int i = 0; i < hits.size(); i++) {
            SearchHit h = hits.get(i);
            sb.append("[").append(i + 1).append("] (").append(h.getDocumentName()).append(") ")
                    .append(h.getContent()).append("\n");
        }
        return new ToolResult(sb.toString().trim(), sources);
    }
}
