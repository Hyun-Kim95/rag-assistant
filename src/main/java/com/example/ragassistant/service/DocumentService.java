package com.example.ragassistant.service;

import com.example.ragassistant.domain.Document;
import com.example.ragassistant.dto.DocumentListResponse;
import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.exception.DocumentNotFoundException;
import com.example.ragassistant.exception.DuplicateDocumentException;
import com.example.ragassistant.exception.EmptyFileException;
import com.example.ragassistant.exception.UnsupportedDocumentFormatException;
import com.example.ragassistant.faq.FaqCatalog;
import com.example.ragassistant.parser.DocumentParser;
import com.example.ragassistant.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentParser documentParser;
    private final VectorStoreService vectorStoreService;

    public DocumentService(DocumentRepository documentRepository, DocumentParser documentParser, VectorStoreService vectorStoreService) {
        this.documentRepository = documentRepository;
        this.documentParser = documentParser;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 업로드된 파일을 검증하고, 텍스트로 파싱한 뒤 DB에 저장
     * 성공 시 저장된 문서의 메타 정보(id, 이름, 길이 등)를 반환
     */
    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        validateFile(file);
        String filename = validateFileAndGetName(file);

        if (documentRepository.existsByName(filename)) {
            throw new DuplicateDocumentException(filename);
        }

        if (!documentParser.supports(filename)) {
            throw new UnsupportedDocumentFormatException(filename);
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new EmptyFileException();
            }
            String content = documentParser.parse(filename,bytes);
            if (content.isBlank()) {
                // txt/md가 공백만 있는 경우 (PDF 빈 텍스트는 parser에서 이미 거부)
                throw new EmptyFileException();
            }
            Document document = Document.newDocument(
                    filename,
                    file.getContentType(),
                    content
            );
            Document saved = documentRepository.save(document);
            // 업로드 직후 자동 인덱싱
            vectorStoreService.indexDocument(saved);

            return toResponse(saved);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private String validateFileAndGetName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new EmptyFileException();
        }
        return filename;
    }

    /**
     * DB에 저장된 모든 문서의 목록을 최신순으로 조회
     * 응답에는 본문 전체(content)가 아닌 메타 정보와 textLength만 포함
     */
    public DocumentListResponse list() {
        List<DocumentResponse> documents = documentRepository.findAll().stream()
                .filter(doc -> !FaqCatalog.isFaqDocument(doc.getName()))
                .map(this::toResponse)
                .toList();
        return new DocumentListResponse(documents);
    }

    /**
     * MultipartFile이 null이 아니고, 파일명이 있으며, 비어 있지 않은지 검사
     * 위 조건을 만족하지 않으면 EmptyFileException
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmptyFileException();
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new EmptyFileException();
        }
    }

    /**
     * Document 도메인 객체를 API 응답용 DocumentResponse로 변환
     * content 전체 대신 length()만 textLength로 넣어 목록/응답 크기를 줄인다.
     */
    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getName(),
                document.getContentType(),
                document.getContent().length(),
                document.getCreatedAt()
        );
    }

    /**
     * 문서 + 연관 chunk + embedding 삭제.
     */
    @Transactional
    public void delete(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (FaqCatalog.isFaqDocument(document.getName())) {
            throw new IllegalArgumentException("시스템 FAQ 문서는 삭제할 수 없습니다.");
        }
        documentRepository.deleteById(id);
    }
}
