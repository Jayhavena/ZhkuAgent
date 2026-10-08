package com.zhku.agent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

public class ResourceDownloadToolTest {

    @Test
    public void testDownloadResource() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "https://example.com/";
        String fileName = "example.html";
        String result = tool.downloadResource(url, fileName);
        assertNotNull(result);
    }
}
