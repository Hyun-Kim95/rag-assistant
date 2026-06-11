package com.example.ragassistant.repository;

import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.domain.StoredChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    private static final RowMapper<SearchHit> LEXICAL_HIT_MAPPER = (rs, rowNum) ->
            new SearchHit(
                    rs.getLong("chunk_id"),
                    rs.getString("document_name"),
                    rs.getString("content"),
                    rs.getDouble("score")
            );
    /**
     * Lexical leg: pg_trgm similarity.
     * 질문 전체 문자열과 chunk 본문의 trigram 유사도.
     * 영문 모델명·설정 키·포트 번호처럼 문서에 그대로 있는 토큰에 유리.
     * 한국어 형태소 분석은 하지 않음 — 코퍼스가 작고 인프라를 늘리지 않기 위한 1차 선택.
     */
    public List<SearchHit> searchLexical(String question, int topK, double minSimilarity) {
        String sql = """
                SELECT c.id AS chunk_id,
                       e.document_name,
                       c.content,
                       similarity(c.content, ?) AS score
                FROM document_chunks c
                JOIN document_embeddings e ON e.chunk_id = c.id
                WHERE similarity(c.content, ?) >= ?
                ORDER BY score DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                LEXICAL_HIT_MAPPER,
                question,
                question,
                minSimilarity,
                topK
        );
    }
}
