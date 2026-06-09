package com.example.ragassistant.repository;

import com.example.ragassistant.domain.SearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * pgvector 컬럼에 벡터 저장
     * JDBC 드라이버에 vector 타입이 없으므로 문자열 '[f1,f2,...]' + ::vector cast 사용
     */
    public void save(Long chunkId, float[] embedding, String documentName) {
        String sql = """
                INSERT INTO document_embeddings (chunk_id, embedding, document_name)
                VALUES (?, ?::vector, ?)
                """;
        jdbcTemplate.update(sql, chunkId, toVectorLiteral(embedding), documentName);
    }

    /**
     * float[] → PostgreSQL vector 리터럴 문자열
     */
    private static String toVectorLiteral(float[] values) {
        StringBuilder sb = new StringBuilder(values.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static final RowMapper<SearchHit> SEARCH_HIT_MAPPER = (rs, rowNum) ->
            new SearchHit(
                    rs.getLong("chunk_id"),
                    rs.getString("document_name"),
                    rs.getString("content"),
                    rs.getDouble("score")
            );

    /**
     * queryVector와 cosine distance 기준 top-k chunk 검색.
     *
     * @param queryVector 질문 embedding
     * @param topK        rag.top-k
     * @return score 내림차순 (유사도 높은 순)
     */
    public List<SearchHit> searchSimilar(float[] queryVector, int topK) {
        String vectorLiteral = toVectorLiteral(queryVector);
        String sql = """
                SELECT c.id AS chunk_id,
                       e.document_name,
                       c.content,
                       1 - (e.embedding <=> ?::vector) AS score
                FROM document_embeddings e
                JOIN document_chunks c ON c.id = e.chunk_id
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                SEARCH_HIT_MAPPER,
                vectorLiteral,
                vectorLiteral,
                topK
        );
    }
}
