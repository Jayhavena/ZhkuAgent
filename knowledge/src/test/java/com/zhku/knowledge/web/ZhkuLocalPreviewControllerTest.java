package com.zhku.knowledge.web;

import com.zhku.knowledge.answer.GroundedAnswerService;
import com.zhku.knowledge.publication.PublishedKnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本机问答预览接口的契约测试。
 *
 * <p>这里保留真实的有依据问答服务，只用固定资料替代数据库，验证 HTTP 边界不会丢失回答模式与出处。</p>
 */
class ZhkuLocalPreviewControllerTest {

    @Test
    void shouldReturnGroundedAnswerAndCitationForCoveredQuestion() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewController(groundedAnswerService())
        ).build();

        mockMvc.perform(post("/zhku/local-preview/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"一学期最多能选多少公共选修课学分？\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json;charset=UTF-8"
                ))
                .andExpect(jsonPath("$.mode").value("GROUNDED"))
                .andExpect(jsonPath("$.answer").value("每学期公共选修课最高不得超过 4 学分。"))
                .andExpect(jsonPath("$.citations[0].sourceTitle")
                        .value("本科生学生手册（2025版）"))
                .andExpect(jsonPath("$.citations[0].sourceLocator")
                        .value("PDF 第 76—77 页，第 8、10 条"))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(76));
    }

    @Test
    void shouldReturnInsufficientEvidenceForQuestionOutsideCurrentScope() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewController(new GroundedAnswerService(keyword -> {
                    throw new AssertionError("资料范围外问题不应进入检索");
                }))
        ).build();

        mockMvc.perform(post("/zhku/local-preview/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"奖学金申请截止日期是什么时候？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.citations").isEmpty());
    }

    @Test
    void shouldRejectBlankQuestion() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewController(groundedAnswerService())
        ).build();

        mockMvc.perform(post("/zhku/local-preview/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION"));
    }

    @Test
    void shouldRejectQuestionLongerThanFiveHundredCharacters() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewController(groundedAnswerService())
        ).build();
        String requestBody = "{\"question\":\"" + "问".repeat(501) + "\"}";

        mockMvc.perform(post("/zhku/local-preview/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION"));
    }

    @Test
    void shouldHideDatabaseFailureBehindServiceUnavailableResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ZhkuLocalPreviewController(new GroundedAnswerService(keyword -> {
                    throw new IllegalStateException("数据库连接参数不应暴露");
                }))
        ).build();

        mockMvc.perform(post("/zhku/local-preview/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"一学期最多能选多少公共选修课学分？\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("本机知识库暂时不可用，请检查本地服务后重试。"));
    }

    private static GroundedAnswerService groundedAnswerService() {
        return new GroundedAnswerService(keyword -> List.of(
                new PublishedKnowledgeChunk(
                        UUID.fromString("2a04fd61-529e-4e6b-8ad6-9666f21a5037"),
                        "本科生学生手册（2025版）",
                        "PDF 第 76—77 页，第 8、10 条",
                        76,
                        "每学期公共选修课最高不得超过 4 学分。"
                )
        ));
    }
}
