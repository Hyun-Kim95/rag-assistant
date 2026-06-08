package com.example.ragassistant.dto;

/**
 * chunk 디버그 API 응답 (본문 포함)
 * @param documentId
 * @param documentName
 * @param chunkIndex
 * @param content
 * @param contentLength
 */
public record ChunkResponse(
        Long documentId,
        String documentName,
        int chunkIndex,
        String content,
        int contentLength
) {
    public static ChunkResponse from(com.example.ragassistant.domain.Chunk chunk) {
        return new ChunkResponse(
                chunk.getDocumentId(),
                chunk.getDocumentName(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getContent().length()
        );
    }
}
