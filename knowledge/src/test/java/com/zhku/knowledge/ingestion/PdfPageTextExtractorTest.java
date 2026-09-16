package com.zhku.knowledge.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageTextExtractorTest {

    @Test
    void shouldExtractSelectedPageWithOneBasedPdfPageNumber()
            throws IOException {
        Path pdf = createTwoPagePdf();

        try {
            List<ExtractedPdfPage> pages = new PdfPageTextExtractor()
                    .extract(pdf, List.of(2));

            assertThat(pages).singleElement().satisfies(page -> {
                assertThat(page.pdfPageNumber()).isEqualTo(2);
                assertThat(page.text()).contains("second policy page");
            });
        } finally {
            // 临时夹具只服务于本次测试，避免在项目中留下无来源的二进制文件。
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void shouldExtractAllPhysicalPagesInAscendingOrder() throws IOException {
        Path pdf = createTwoPagePdf();

        try {
            List<ExtractedPdfPage> pages = new PdfPageTextExtractor()
                    .extractAll(pdf);

            assertThat(pages).hasSize(2);
            assertThat(pages.get(0)).satisfies(page -> {
                assertThat(page.pdfPageNumber()).isEqualTo(1);
                assertThat(page.text()).contains("first policy page");
            });
            assertThat(pages.get(1)).satisfies(page -> {
                assertThat(page.pdfPageNumber()).isEqualTo(2);
                assertThat(page.text()).contains("second policy page");
            });
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void shouldRejectPageOutsidePdfRange() throws IOException {
        Path pdf = createTwoPagePdf();

        try {
            assertThatThrownBy(() -> new PdfPageTextExtractor()
                    .extract(pdf, List.of(3)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("超出 PDF 页数范围");
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    @Test
    void shouldExtractCitableTextFromLocalStudentHandbook()
            throws IOException {
        Path handbook = Path.of(
                "data/raw/2025版学生手册2025.9.2.pdf"
        );
        // 原始资料被 Git 忽略，持续集成环境没有该文件时应跳过而不是伪造资料。
        Assumptions.assumeTrue(Files.isRegularFile(handbook));

        ExtractedPdfPage page = new PdfPageTextExtractor()
                .extract(handbook, List.of(76))
                .getFirst();

        assertThat(page.pdfPageNumber()).isEqualTo(76);
        assertThat(page.text())
                .as("真实学生手册的候选引用页必须能提取出文本")
                .isNotBlank();
    }

    private static Path createTwoPagePdf() throws IOException {
        Path pdf = Files.createTempFile("zhku-pdf-page-extractor-", ".pdf");

        try (PDDocument document = new PDDocument()) {
            addPageWithText(document, "first policy page");
            addPageWithText(document, "second policy page");
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private static void addPageWithText(
            PDDocument document,
            String text
    ) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);

        try (PDPageContentStream stream = new PDPageContentStream(
                document,
                page
        )) {
            stream.beginText();
            stream.setFont(PDType1Font.HELVETICA, 12);
            stream.newLineAtOffset(72, 720);
            stream.showText(text);
            stream.endText();
        }
    }
}
