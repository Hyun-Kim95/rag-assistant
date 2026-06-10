package com.example.ragassistant.parser;

import com.example.ragassistant.exception.DocumentParseException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentParserTest {

    private DocumentParser parser;

    // 각 테스트마다 새 DocumentParser 인스턴스를 준비
    @BeforeEach
    void setUp() {
        parser = new DocumentParser();
    }

    // supports(): txt/md/pdf(대소문자 무관)는 true, 그 외 확장자는 false
    @Test
    void supports_acceptsTxtMdPdf() {
        assertThat(parser.supports("readme.md")).isTrue();
        assertThat(parser.supports("notes.TXT")).isTrue();
        assertThat(parser.supports("report.pdf")).isTrue();
        assertThat(parser.supports("image.png")).isFalse();
    }

    // parse(): txt/md는 UTF-8 바이트를 그대로 문자열로 변환 (한글 포함)
    @Test
    void parse_txtAndMd_asUtf8() {
        byte[] bytes = "한글 본문".getBytes(StandardCharsets.UTF_8);
        assertThat(parser.parse("note.txt", bytes)).isEqualTo("한글 본문");
        assertThat(parser.parse("note.md", bytes)).isEqualTo("한글 본문");
    }

    // parse(): 텍스트가 embedded된 PDF에서 본문 일부가 추출되는지
    @Test
    void parse_pdf_extractsEmbeddedText() throws IOException {
        byte[] pdf = pdfWithText();
        String text = parser.parse("sample.pdf", pdf);
        assertThat(text).contains("RAG Assistant PDF test");
        assertThat(text).contains("pgvector");
    }

    // parse(): PDF 확장자지만 바이트가 손상된 경우 DocumentParseException
    @Test
    void parse_pdf_corruptBytes_throwsDocumentParseException() {
        // pdf 구조가 없는 일반 텍스트 생성
        byte[] garbage = "not a pdf".getBytes(StandardCharsets.UTF_8);
        // pdf 파싱 분기로는 들어가는데 실제 pdf가 아님
        assertThatThrownBy(() -> parser.parse("bad.pdf", garbage))
                .isInstanceOf(DocumentParseException.class)
                .hasMessageContaining("PDF를 읽을 수 없습니다");
    }

    // 테스트용 1페이지 텍스트 PDF 생성 (외부 fixture 파일 불필요)
    private static byte[] pdfWithText() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("RAG Assistant PDF test pgvector"); // ASCII 위주 (Helvetica는 한글 미지원)
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
