package com.example.ragassistant.domain;

import lombok.Getter;

/**
 * document_chunks 테이블에 저장된 chunk.
 * 메모리용 Chunk(domain.Chunk)와 구분: DB PK(id)가 있다.
 */
@Getter
public class StoredChunk {

    private final Long id;              // document_chunks.id
    private final Long documentId;
    private final String documentName;
    private final int chunkIndex;
    private final String content;

    public StoredChunk(Long id, Long documentId, String documentName,
                       int chunkIndex, String content) {
        this.id = id;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    /**
     * Chunker가 만든 Chunk → INSERT 전 객체
     */
    public static StoredChunk fromChunk(com.example.ragassistant.domain.Chunk chunk) {
        return new StoredChunk(
                null,
                chunk.getDocumentId(),
                chunk.getDocumentName(),
                chunk.getChunkIndex(),
                chunk.getContent()
        );
    }

}
