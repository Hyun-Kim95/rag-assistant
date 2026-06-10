package com.example.ragassistant.repository;

import com.example.ragassistant.domain.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Document> ROW_MAPPER = (rs, rowNum) -> toDocument(rs);

    public Document save(Document document) {
        String sql = """
                INSERT INTO documents (name, content_type, content, created_at)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """;
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                document.getName(),
                document.getContentType(),
                document.getContent(),
                Timestamp.valueOf(document.getCreatedAt())
        );
        document.setId(id);
        return document;
    }

    public Optional<Document> findById(Long id) {
        String sql = """
                SELECT id, name, content_type, content, created_at
                FROM documents
                WHERE id = ?
                """;
        List<Document> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public List<Document> findAll() {
        String sql = """
                SELECT id, name, content_type, content, created_at
                FROM documents
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    private static Document toDocument(ResultSet rs) throws SQLException {
        return new Document(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("content_type"),
                rs.getString("content"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", id);
    }

    // 중복 업로드 검사용 (Part C)
    public boolean existsByName(String name) {
        String sql = "SELECT EXISTS(SELECT 1 FROM documents WHERE name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, name);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<Document> findByName(String name) {
        String sql = """
                SELECT id, name, content_type, content, created_at
                FROM documents
                WHERE name = ?
                """;
        List<Document> results = jdbcTemplate.query(sql, ROW_MAPPER, name);
        return results.stream().findFirst();
    }
}
