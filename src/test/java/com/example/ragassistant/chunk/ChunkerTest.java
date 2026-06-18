package com.example.ragassistant.chunk;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.Chunk;
import com.example.ragassistant.domain.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ChunkerTest {
    private Chunker chunker(int size, int overlap) {
        RagProperties props = new RagProperties(size, overlap, 3, 0.2, 768, false, 10, 0.1, 60, 30, 6, false);
        return new Chunker(props);
    }

    private Document doc(String content) {
        return new Document(1L, "test.md", "text/plain", content, LocalDateTime.now());
    }

    // 빈 텍스트인 경우
    @Test
    void emptyContent_returnsEmptyList() {
        List<Chunk> chunks = chunker(700, 100).split(doc(""));
        assertThat(chunks).isEmpty();
    }

    // 텍스트가 chunk size보다 작은 경우
    @Test
    void shortContent_returnsSingleChunk() {
        String content = "짧은 문서";
        List<Chunk> chunks = chunker(700, 100).split(doc(content));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).getContent()).isEqualTo(content);
    }

    // 분할이 실제로 일어났는지
    @Test
    void longContent_splitsIntoMultipleChunks() {
        String content = "가".repeat(2000);
        List<Chunk> chunks = chunker(700, 100).split(doc(content));

        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).getChunkIndex()).isEqualTo(1);
    }

    // overlap 검증
    @Test
    void adjacentChunks_overlap() {
        // 0~1999 숫자 문자열 → 어디서 잘렸는지 추적 가능
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append(String.format("%04d", i));
        }
        String content = sb.toString();

        int overlap = 100;
        List<Chunk> chunks = chunker(700, overlap).split(doc(content));
        assertThat(chunks.size()).isGreaterThan(1);

        String first = chunks.get(0).getContent();
        String second = chunks.get(1).getContent();

        String tailOfFirst = first.substring(first.length() - overlap);    // 끝 100자
        String headOfSecond = second.substring(0, overlap);                         // 앞 100자
        assertThat(headOfSecond).isEqualTo(tailOfFirst);
    }

    // 메타데이터 확인
    @Test
    void chunks_haveMetadata() {
        Document document = new Document(42L, "report.md", "text/plain", "가".repeat(1500), LocalDateTime.now());
        List<Chunk> chunks = chunker(700, 100).split(document);

        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.getDocumentId()).isEqualTo(42L);
            assertThat(c.getDocumentName()).isEqualTo("report.md");
        });
        assertThat(chunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(chunks.get(1).getChunkIndex()).isEqualTo(1);
    }

    // \n\n이 우선순위로 동작하는지
    @Test
    void splitsAtParagraphBoundary() {
        String part1 = "가".repeat(650); // Chunker.findBreakPoint에서 마지막 120자를 검사하기 때문에
        String part2 = "나".repeat(500);
        String content = part1 + "\n\n" + part2;

        List<Chunk> chunks = chunker(700, 100).split(doc(content));

        // 첫 chunk가 part2 '나'로 시작하지 않으면 문단 앞에서 끊긴 것
        assertThat(chunks.get(0).getContent()).doesNotContain("나");
        // part2가 chunk1에 들어갔는지
        assertThat(chunks.get(1).getContent()).contains("나");
    }

    // chunk size > 0
    @Test
    void invalidChunkSize_throws() {
        assertThatThrownBy(() -> chunker(0, 100).split(doc("test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-size");
    }

    // overlap < chunk size
    @Test
    void overlapGreaterThanSize_throws() {
        assertThatThrownBy(() -> chunker(700, 700).split(doc("test")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk-overlap");
    }
}
