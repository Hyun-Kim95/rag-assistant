package com.example.ragassistant.agent.tool;

import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.domain.Chunk;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.service.ChunkService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * read_document: 특정 문서의 '실제 본문'을 청크 범위로 반환한다.
 * search_documents가 top-k 단편만 주는 것과 달리, 지정 문서를 연속 구간으로 읽게 해
 * "그 문서의 X 부분 보여줘" 같은 멀티홉을 가능케 한다.
 * 토큰 폭증을 막기 위해 청크 수(readMaxChunks)·총 길이(readMaxChars) 상한을 둔다.
 */
@Component
public class ReadDocumentTool implements AgentTool {

    private final ChunkService chunkService;
    private final AgentProperties props;

    public ReadDocumentTool(ChunkService chunkService, AgentProperties props) {
        this.chunkService = chunkService;
        this.props = props;
    }

    @Override
    public String name() {
        return "read_document";
    }

    @Override
    public String description() {
        return "특정 문서(documentId)의 실제 본문을 청크 범위로 읽는다. "
                + "documentId는 추측하지 말고 list_documents 또는 search_documents 결과의 id를 사용한다. "
                + "fromChunk(시작 청크, 기본 0)와 maxChunks(읽을 청크 수)로 범위를 지정한다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "documentId", Map.of("type", "integer", "description", "읽을 문서 id"),
                        "fromChunk", Map.of("type", "integer", "description", "시작 청크 index(0부터, 기본 0)"),
                        "maxChunks", Map.of("type", "integer", "description", "읽을 청크 수(기본 " + props.readMaxChunks() + ")")),
                "required", List.of("documentId"));
    }

    @Override
    public ToolResult execute(JsonNode arguments) {
        if (arguments.path("documentId").isMissingNode()) {
            return ToolResult.text("오류: documentId 인자가 필요합니다.");
        }
        long id = arguments.path("documentId").asLong();
        int fromChunk = Math.max(0, arguments.path("fromChunk").asInt(0));
        int maxChunks = arguments.path("maxChunks").asInt(props.readMaxChunks());
        if (maxChunks <= 0 || maxChunks > props.readMaxChunks()) {
            maxChunks = props.readMaxChunks();   // 모델이 과도하게 요청해도 상한으로 클램프
        }

        List<Chunk> chunks;
        try {
            chunks = chunkService.chunkByDocumentId(id);
        } catch (IllegalArgumentException e) {
            // 존재하지 않는 id → 모델이 list_documents로 복구하도록 오류 텍스트 반환
            return ToolResult.text("문서를 찾을 수 없습니다: id=" + id + ". list_documents로 id를 먼저 확인하세요.");
        }
        if (chunks.isEmpty()) {
            return ToolResult.text("문서 id=" + id + "에 본문 청크가 없습니다.");
        }

        int total = chunks.size();
        if (fromChunk >= total) {
            return ToolResult.text("fromChunk(" + fromChunk + ")가 청크 수(" + total + ")를 벗어났습니다.");
        }
        int to = Math.min(fromChunk + maxChunks, total);
        String docName = chunks.get(0).getDocumentName();

        StringBuilder sb = new StringBuilder();
        sb.append("문서 '").append(docName).append("' 본문 (청크 ")
                .append(fromChunk).append("~").append(to - 1).append(" / 총 ").append(total).append("):\n");
        int budget = props.readMaxChars();
        int lastRead = fromChunk;
        for (int i = fromChunk; i < to; i++) {
            String seg = chunks.get(i).getContent();
            if (sb.length() + seg.length() > budget) {
                break;  // 총 길이 상한 도달 → 여기까지만
            }
            sb.append("[#").append(i).append("] ").append(seg).append("\n");
            lastRead = i;
        }
        if (lastRead < total - 1) {
            sb.append("(이 문서는 총 ").append(total).append("청크 중 ")
                    .append(fromChunk).append("~").append(lastRead).append(" 범위입니다.)");
        }

        // 문서 단위 출처 1건 누적(grounded=true 근거). chunkId 자리에 documentId를 doc-level 마커로 사용.
        SourceCitation cite = new SourceCitation(docName, id, snippet(chunks.get(fromChunk).getContent()), 1.0);
        return new ToolResult(sb.toString().trim(), List.of(cite));
    }

    private static String snippet(String text) {
        if (text == null) return "";
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
