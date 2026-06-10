package com.example.ragassistant.controller;

import com.example.ragassistant.dto.DocumentListResponse;
import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Documents", description = "문서 업로드·목록")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "문서 업로드", description = "TXT / MD / PDF 업로드 후 DB 저장")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "문서 목록", description = "저장된 문서 메타 정보 목록")
    @GetMapping
    public DocumentListResponse list(){
        return documentService.list();
    }

    @Operation(summary = "문서 삭제", description = "문서와 연관 chunk·embedding을 함께 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();  // 204
    }
}
