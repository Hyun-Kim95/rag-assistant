package com.example.ragassistant.repository;

import com.example.ragassistant.domain.StoredChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * document_chunks에 INSERT 후 DB가 부여한 id를 채운 StoredChunk 반환
     */
    public StoredChunk save(StoredChunk chunk) {
        String sql = """
                INSERT INTO document_chunks (document_id, chunk_index, content)
                VALUES (?, ?, ?)
                RETURNING id
                """;
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                chunk.getDocumentId(),
                chunk.getChunkIndex(),
                chunk.getContent()
        );
        return new StoredChunk(
                id,
                chunk.getDocumentId(),
                chunk.getDocumentName(),
                chunk.getChunkIndex(),
                chunk.getContent()
        );
    }
}
