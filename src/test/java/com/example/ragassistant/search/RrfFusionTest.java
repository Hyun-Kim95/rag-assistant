package com.example.ragassistant.search;

import com.example.ragassistant.domain.SearchHit;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class RrfFusionTest {

    // vector·lexical 양쪽에 모두 있는 chunk가 최종 1위인지 확인
    @Test
    void fuse_prefersChunkPresentInBothLegs() {
        SearchHit shared = new SearchHit(1L, "a.md", "shared content", 0.9);
        SearchHit vectorOnly = new SearchHit(2L, "b.md", "vector only", 0.85);
        SearchHit lexicalOnly = new SearchHit(3L, "c.md", "lexical only", 0.8);
        List<SearchHit> vectorHits = List.of(shared, vectorOnly);
        List<SearchHit> lexicalHits = List.of(shared, lexicalOnly);
        List<SearchHit> fused = RrfFusion.fuse(vectorHits, lexicalHits, 60, 3);
        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).getChunkId()).isEqualTo(1L); // 양쪽 1위 → RRF 1위
    }

    // limit 파라미터가 실제로 잘리는지 확인
    @Test
    void fuse_respectsLimit() {
        List<SearchHit> vectorHits = List.of(
                new SearchHit(1L, "a", "c1", 0.9),
                new SearchHit(2L, "a", "c2", 0.8)
        );
        List<SearchHit> fused = RrfFusion.fuse(vectorHits, List.of(), 60, 1);
        assertThat(fused).hasSize(1);
    }
}
