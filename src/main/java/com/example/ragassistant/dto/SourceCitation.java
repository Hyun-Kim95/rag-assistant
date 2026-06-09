package com.example.ragassistant.dto;

/**
 * 답변 근거로 사용된 chunk 1건 (출처)
 *
 * @param documentName 원본 파일명
 * @param chunkId      document_chunks.id
 * @param snippet      chunk 본문 일부 (응답 크기 제한용)
 * @param score        cosine similarity
 */
public record SourceCitation(
        String documentName,
        Long chunkId,
        String snippet,
        double score
) {
    private static final int SNIPPET_MAX = 200;

    public static SourceCitation from(com.example.ragassistant.domain.SearchHit hit) {
        String snippet = truncate(hit.getContent());
        return new SourceCitation(
                hit.getDocumentName(),
                hit.getChunkId(),
                snippet,
                hit.getScore()
        );
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= SNIPPET_MAX) {
            return text;
        }
        return text.substring(0, SNIPPET_MAX) + "...";
    }
}
