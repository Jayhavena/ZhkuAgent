package com.yupi.yuaiagent.zhku.knowledge.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 从本地 PDF 按物理页码提取文本。
 *
 * <p>该组件只负责忠实提取，绝不判断规则是否仍然有效，也不直接写入学生可见知识库。</p>
 */
public final class PdfPageTextExtractor {

    /**
     * 提取指定 PDF 页。页码从 1 开始，与 PDF 阅读器显示的物理页一致。
     *
     * @param pdf PDF 文件路径
     * @param requestedPageNumbers 需要提取的物理页码集合
     * @return 按升序、去重后的页文本
     */
    public List<ExtractedPdfPage> extract(
            Path pdf,
            Collection<Integer> requestedPageNumbers
    ) throws IOException {
        Objects.requireNonNull(pdf, "PDF 文件路径不能为空");
        Objects.requireNonNull(requestedPageNumbers, "PDF 页码集合不能为空");

        if (!Files.isRegularFile(pdf)) {
            throw new IllegalArgumentException("PDF 文件不存在或不是普通文件：" + pdf);
        }

        List<Integer> pageNumbers = requestedPageNumbers.stream()
                .peek(this::validatePositivePageNumber)
                // 去重并排序避免同一页被重复入库，确保每次提取结果稳定。
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            int totalPages = document.getNumberOfPages();
            pageNumbers.forEach(pageNumber -> validatePageRange(
                    pageNumber,
                    totalPages
            ));

            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);

            return pageNumbers.stream()
                    .map(pageNumber -> extractPage(
                            document,
                            textStripper,
                            pageNumber
                    ))
                    .toList();
        }
    }

    private ExtractedPdfPage extractPage(
            PDDocument document,
            PDFTextStripper textStripper,
            int pageNumber
    ) {
        try {
            textStripper.setStartPage(pageNumber);
            textStripper.setEndPage(pageNumber);
            return new ExtractedPdfPage(
                    pageNumber,
                    textStripper.getText(document).trim()
            );
        } catch (IOException exception) {
            throw new PdfPageExtractionException(
                    "无法提取 PDF 第 " + pageNumber + " 页文本",
                    exception
            );
        }
    }

    private void validatePositivePageNumber(int pageNumber) {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("PDF 页码必须从 1 开始");
        }
    }

    private void validatePageRange(int pageNumber, int totalPages) {
        if (pageNumber > totalPages) {
            throw new IllegalArgumentException(
                    "请求页码 " + pageNumber
                            + " 超出 PDF 页数范围（共 " + totalPages + " 页）"
            );
        }
    }

    /** 将受检 I/O 异常转换为调用方可识别的提取失败，不吞掉原始原因。 */
    private static final class PdfPageExtractionException
            extends RuntimeException {

        private PdfPageExtractionException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}
