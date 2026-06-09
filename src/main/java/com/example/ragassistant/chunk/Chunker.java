package com.example.ragassistant.chunk;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.Chunk;
import com.example.ragassistant.domain.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Chunker {

    private final RagProperties ragProperties;

    public Chunker(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    /**
     * Document 전체 텍스트를 chunk 목록으로 분할
     * metadata(documentId, documentName, chunkIndex)를 각 chunk에 붙인다.
     */
    public List<Chunk> split(Document document) {
        String text = normalize(document.getContent());
        if (text.isEmpty()) {
            return List.of();
        }
        int chunkSize = ragProperties.chunkSize();
        int overlap = ragProperties.chunkOverlap();
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunk-size must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunk-overlap must be >= 0 and < chunk-size");
        }
        if (text.length() <= chunkSize) {
            return List.of(new Chunk(
                    document.getId(),
                    document.getName(),
                    0,
                    text
            ));
        }
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int rawEnd = Math.min(start + chunkSize, text.length());
            int end = findBreakPoint(text, start, rawEnd);
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new Chunk(
                        document.getId(),
                        document.getName(),
                        index++,
                        piece
                ));
            }
            if (end >= text.length()) {
                break;
            }
            int nextStart = end - overlap;
            if (nextStart <= start) {
                nextStart = end; // 무한 루프 방지
            }
            start = nextStart;
        }
        return chunks;
    }

    /**
     * null, 공백 정리
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /**
     * rawEnd 근처에서 자연스러운 끊김 위치를 찾는다.
     * 우선순위: 문단(\n\n) → 줄바꿈 → 문장(. ) → 그냥 rawEnd
     */
    private int findBreakPoint(String text, int start, int rawEnd) {
        if (rawEnd >= text.length()) {
            return text.length();
        }
        int searchFrom = Math.max(start + 1, rawEnd - 120); // 끝 120자 안에서 탐색
        int paragraph = text.lastIndexOf("\n\n", rawEnd);
        if (paragraph >= searchFrom) {
            return paragraph + 2;
        }
        int line = text.lastIndexOf('\n', rawEnd);
        if (line >= searchFrom) {
            return line + 1;
        }
        int sentence = text.lastIndexOf(". ", rawEnd);
        if (sentence >= searchFrom) {
            return sentence + 2;
        }
        return rawEnd;
    }
}
