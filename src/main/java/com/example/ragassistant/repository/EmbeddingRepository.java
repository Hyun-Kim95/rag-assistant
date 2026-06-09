package com.example.ragassistant.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
