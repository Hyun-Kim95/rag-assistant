package com.example.ragassistant.agent.tool;

import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.domain.Document;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.exception.DocumentNotFoundException;
import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.service.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * summarize_document: 특정 문서 '전체'를 근거로 요약한다.
 * top-k 검색이 놓치는 전반 구조를 다루려는 도구. 본문을 예산(summarizeMaxChars) 내로 잘라
 * LLM(ChatModelClient = @Primary 라우터)에 요약을 위임한다(MVP: truncate 방식).
 * tool 안에서 LLM을 한 번 더 호출하므로 지연이 늘 수 있다(agent.timeout-ms 여유 필요).
 */
@Component
public class SummarizeDocumentTool implements AgentTool {

    private final DocumentService documentService;
    private final ChatModelClient chatModelClient;
    private final AgentProperties props;

    public SummarizeDocumentTool(DocumentService documentService,
                                 ChatModelClient chatModelClient,
                                 AgentProperties props) {
        this.documentService = documentService;
        this.chatModelClient = chatModelClient;
        this.props = props;
    }

    @Override
    public String name() {
        return "summarize_document";
    }

    @Override
    public String description() {
        return "특정 문서(documentId) 전체 내용을 근거로 요약한다. 문서 전반/핵심을 묻는 질문에 쓴다. "
                + "documentId는 추측하지 말고 list_documents 결과의 id를 사용한다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "documentId", Map.of("type", "integer", "description", "요약할 문서 id")),
                "required", List.of("documentId"));
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        if (arguments.path("documentId").isMissingNode()) {
            return ToolResult.text("오류: documentId 인자가 필요합니다.");
        }
        long id = arguments.path("documentId").asLong();

        Document doc;
        try {
            doc = documentService.find(id);
        } catch (DocumentNotFoundException e) {
            return ToolResult.text("문서를 찾을 수 없습니다: id=" + id + ". list_documents로 id를 먼저 확인하세요.");
        }

        String content = doc.getContent();
        boolean truncated = content.length() > props.summarizeMaxChars();
        String body = truncated ? content.substring(0, props.summarizeMaxChars()) : content;

        String prompt = """
                다음 문서 내용을 한국어로 간결하게 요약하라.
                - 문서에 실제로 있는 내용만 사용하고 지어내지 않는다.
                - 핵심 주제와 항목 위주로 5~8줄 이내.

                [문서: %s]
                %s
                """.formatted(doc.getName(), body);

        String summary = chatModelClient.chat(prompt);
        String note = truncated ? "\n(주의: 문서가 길어 앞부분 " + props.summarizeMaxChars() + "자 기준 요약)" : "";

        // 문서 단위 출처 1건 누적(grounded=true 근거).
        SourceCitation cite = new SourceCitation(doc.getName(), id, snippet(content), 1.0);
        return new ToolResult("문서 '" + doc.getName() + "' 요약:\n" + summary + note, List.of(cite));
    }

    private static String snippet(String text) {
        if (text == null) return "";
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
