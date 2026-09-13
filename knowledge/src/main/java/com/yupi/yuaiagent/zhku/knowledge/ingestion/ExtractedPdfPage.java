package com.yupi.yuaiagent.zhku.knowledge.ingestion;

import java.util.Objects;

/**
 * 一页 PDF 的原始提取结果。
 *
 * <p>保留物理 PDF 页码而非手册印刷页码，后续引用可稳定回到用户实际上传的文件。</p>
 */
public record ExtractedPdfPage(
        int pdfPageNumber,
        String text
) {

    public ExtractedPdfPage {
        if (pdfPageNumber < 1) {
            throw new IllegalArgumentException("PDF 页码必须从 1 开始");
        }
        text = Objects.requireNonNull(text, "提取文本不能为空");
    }
}
