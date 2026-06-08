package com.example.ragassistant.domain;

import lombok.Getter;

@Getter
public class Chunk {

    private final Long documentId;
    private final String documentName;
    private final int chunkIndex;
    private final String content;

    public Chunk(Long documentId, String documentName, int chunkIndex, String content) {
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

}
