package com.example.ragassistant.service;

import com.example.ragassistant.chunk.Chunker;
import com.example.ragassistant.domain.Chunk;
import com.example.ragassistant.domain.Document;
import com.example.ragassistant.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChunkService {

    private final DocumentRepository documentRepository;
    private final Chunker chunker;

    public ChunkService(DocumentRepository documentRepository, Chunker chunker) {
        this.documentRepository = documentRepository;
        this.chunker = chunker;
    }

    /**
     * documentId로 문서를 조회한 뒤 chunk 목록을 반환
     */
    public List<Chunk> chunkByDocumentId(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));
        return chunker.split(document);
    }
}
