package com.example.ragassistant.faq;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FaqCatalogTest {

    // FAQ chunk가 비어 있지 않고, 정책 키워드를 포함하는지
    @Test
    void chunks_containNoAnswerPolicyKeywords() {
        assertThat(FaqCatalog.chunks()).hasSizeGreaterThanOrEqualTo(3);
        String policyChunk = FaqCatalog.chunks().get(0);
        assertThat(policyChunk)
                .contains("hits")
                .contains("min-score")
                .contains("LLM")
                .contains("no-answer");
    }

    // fullContent는 chunk를 합친 것과 동기화되는지
    @Test
    void fullContent_joinsAllChunks() {
        assertThat(FaqCatalog.fullContent())
                .contains(FaqCatalog.chunks().get(0).substring(0, 20));
    }

    // 예약 문서명 판별
    @Test
    void isFaqDocument_recognizesReservedName() {
        assertThat(FaqCatalog.isFaqDocument(FaqCatalog.DOCUMENT_NAME)).isTrue();
        assertThat(FaqCatalog.isFaqDocument("README.md")).isFalse();
    }
}
