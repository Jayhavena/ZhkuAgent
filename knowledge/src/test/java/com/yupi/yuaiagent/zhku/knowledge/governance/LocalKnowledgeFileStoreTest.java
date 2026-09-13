package com.yupi.yuaiagent.zhku.knowledge.governance;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalKnowledgeFileStoreTest {

    @TempDir
    Path storageDirectory;

    @Test
    void shouldStoreReadablePdfWithControlledKeyAndChecksum()
            throws IOException {
        LocalKnowledgeFileStore store = new LocalKnowledgeFileStore(
                storageDirectory
        );

        StoredKnowledgeFile stored = store.store(
                "handbook.pdf",
                new ByteArrayInputStream(validPdfBytes())
        );

        assertThat(stored.storageKey())
                .doesNotContain("/", "\\")
                .endsWith(".pdf");
        assertThat(stored.sha256()).matches("^[A-F0-9]{64}$");
        assertThat(stored.size()).isPositive();
        assertThat(storageDirectory.resolve(stored.storageKey()))
                .exists()
                .isRegularFile();
    }

    @Test
    void shouldRejectNonPdfFilenameEvenWhenContentIsReadable()
            throws IOException {
        LocalKnowledgeFileStore store = new LocalKnowledgeFileStore(
                storageDirectory
        );

        assertThatThrownBy(() -> store.store(
                "handbook.txt",
                new ByteArrayInputStream(validPdfBytes())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 PDF");
    }

    @Test
    void shouldRejectEmptyPdfContent() {
        LocalKnowledgeFileStore store = new LocalKnowledgeFileStore(
                storageDirectory
        );

        assertThatThrownBy(() -> store.store(
                "empty.pdf",
                new ByteArrayInputStream(new byte[0])
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldRejectUnreadablePdfContent() {
        LocalKnowledgeFileStore store = new LocalKnowledgeFileStore(
                storageDirectory
        );

        assertThatThrownBy(() -> store.store(
                "broken.pdf",
                new ByteArrayInputStream("not a pdf".getBytes())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法读取");
    }

    private static byte[] validPdfBytes() throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(
                    document,
                    page
            )) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("local handbook policy");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
