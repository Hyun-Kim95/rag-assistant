package com.example.ragassistant.agent.tool;

import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.service.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * list_documents: 업로드된 문서 목록(id, 파일명, 길이)을 반환한다(인자 없음).
 */
@Component
public class ListDocumentsTool implements AgentTool {

    private final DocumentService documentService;

    public ListDocumentsTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "list_documents";
    }

    @Override
    public String description() {
        return "업로드된 문서의 목록(id, 파일명)을 조회한다. 어떤 문서가 있는지 물을 때 사용한다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        List<DocumentResponse> docs = documentService.list().documents();
        if (docs.isEmpty()) {
            return ToolResult.text("업로드된 문서가 없습니다.");
        }
        StringBuilder sb = new StringBuilder("문서 ").append(docs.size()).append("건:\n");
        for (DocumentResponse d : docs) {
            sb.append("- id=").append(d.id())
                    .append(", 파일명=").append(d.name())
                    .append(", 길이=").append(d.textLength()).append("\n");
        }
        return ToolResult.text(sb.toString().trim());
    }
}
