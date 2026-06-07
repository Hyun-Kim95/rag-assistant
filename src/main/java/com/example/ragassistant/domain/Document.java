package com.example.ragassistant.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
public class Document {

    // returning id로 db가 부여한 id 반영
    @Setter
    private Long id;
    private final String name;
    private final String contentType;
    private final String content;
    private final LocalDateTime createdAt;

    public Document(Long id, String name, String contentType, String content, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Document newDocument(String name, String contentType, String content) {
        return new Document(null, name, contentType, content, LocalDateTime.now());
    }

}
