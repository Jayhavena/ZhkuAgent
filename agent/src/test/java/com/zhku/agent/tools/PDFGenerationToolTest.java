package com.zhku.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "sample-report.pdf";
        String content = "ZhkuAgent synthetic report.";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
