package com.example.ragassistant.service;

import com.example.ragassistant.chunk.Chunker;
import com.example.ragassistant.domain.Chunk;
import com.example.ragassistant.domain.Document;
import com.example.ragassistant.domain.StoredChunk;
import com.example.ragassistant.repository.ChunkRepository;
import com.example.ragassistant.repository.EmbeddingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 문서 1건을 chunk → embed → DB 저장하는 인덱싱 파이프라인
 */
@Service
public class VectorStoreService {

    private final Chunker chunker;
    private final EmbeddingService embeddingService;
    private final ChunkRepository chunkRepository;
    private final EmbeddingRepository embeddingRepository;

    public VectorStoreService(
            Chunker chunker,
            EmbeddingService embeddingService,
            ChunkRepository chunkRepository,
            EmbeddingRepository embeddingRepository
    ) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * 업로드 직후 호출. document는 documents 테이블에 이미 저장된 상태(id 필수).
     *
     * @return 저장된 chunk 수
     */
    @Transactional
    public int indexDocument(Document document) {
        List<Chunk> chunks = chunker.split(document);
        for (Chunk chunk : chunks) {
            // chunk 텍스트 DB 저장
            StoredChunk saved = chunkRepository.save(StoredChunk.fromChunk(chunk));
            // Ollama embedding
            float[] vector = embeddingService.embed(saved.getContent());
            // pgvector 저장
            embeddingRepository.save(
                    saved.getId(),
                    vector,
                    saved.getDocumentName()
            );
        }
        return chunks.size();
    }
}
