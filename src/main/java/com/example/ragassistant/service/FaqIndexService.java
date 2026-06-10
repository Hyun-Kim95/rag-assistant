package com.example.ragassistant.service;

import com.example.ragassistant.domain.Document;
import com.example.ragassistant.faq.FaqCatalog;
import com.example.ragassistant.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FaqIndexService {
    private static final Logger log = LoggerFactory.getLogger(FaqIndexService.class);
    private final DocumentRepository documentRepository;
    private final VectorStoreService vectorStoreService;

    public FaqIndexService(DocumentRepository documentRepository, VectorStoreService vectorStoreService) {
        this.documentRepository = documentRepository;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional
    public void ensureFaqIndexed() {
        String expected = FaqCatalog.fullContent();
        Optional<Document> existing = documentRepository.findByName(FaqCatalog.DOCUMENT_NAME);
        if (existing.isPresent()) {
            Document doc = existing.get();
            if (expected.equals(doc.getContent())) {
                log.info("시스템 FAQ 최신 상태 — 스킵 (documentId={})", doc.getId());
                return;
            }
            log.info("시스템 FAQ 문구 변경 감지 — 재인덱싱 (documentId={})", doc.getId());
            documentRepository.deleteById(doc.getId());
        }
        Document saved = documentRepository.save(
                Document.newDocument(
                        FaqCatalog.DOCUMENT_NAME,
                        FaqCatalog.CONTENT_TYPE,
                        expected
                )
        );
        int count = vectorStoreService.indexRawChunks(saved, FaqCatalog.chunks());
        log.info("시스템 FAQ 인덱싱 완료 — chunks={}", count);
    }
}
