package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.governance.CandidateReviewService;
import com.zhku.knowledge.governance.CandidateReviewStatus;
import com.zhku.knowledge.governance.KnowledgeCandidate;
import com.zhku.knowledge.governance.KnowledgeGovernancePublisher;
import com.zhku.knowledge.governance.KnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeSubmission;
import com.zhku.knowledge.governance.KnowledgeSubmissionService;
import com.zhku.knowledge.governance.KnowledgeSubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ZhkuLocalAdminControllerTest {

    private static final UUID SUBMISSION_ID = UUID.fromString(
            "b14cf842-c761-4a0e-9b01-a3c239fcf9a4"
    );
    private static final UUID CANDIDATE_ID = UUID.fromString(
            "d2f47611-b1ae-43d8-8fa0-32b20cd17059"
    );

    private KnowledgeSubmissionService submissionService;
    private CandidateReviewService reviewService;
    private KnowledgeGovernancePublisher publisher;
    private KnowledgeGovernanceRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submissionService = mock(KnowledgeSubmissionService.class);
        reviewService = mock(CandidateReviewService.class);
        publisher = mock(KnowledgeGovernancePublisher.class);
        repository = mock(KnowledgeGovernanceRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ZhkuLocalAdminController(
                submissionService, reviewService, publisher, repository
        )).setControllerAdvice(new ZhkuLocalAdminExceptionHandler()).build();
    }

    @Test
    void shouldCreateDraftSubmissionWithoutExposingStorageKey() throws Exception {
        when(submissionService.createSubmission(any(), any(InputStream.class)))
                .thenReturn(submission(KnowledgeSubmissionStatus.DRAFT));

        mockMvc.perform(validUpload())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.id").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void shouldRejectNonPdfBeforeCallingSubmissionService() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "handbook.txt", MediaType.TEXT_PLAIN_VALUE,
                "not a pdf".getBytes()
        );

        mockMvc.perform(multipart("/zhku/local-admin/submissions")
                        .file(invalidFile)
                        .param("title", "测试学生手册")
                        .param("sourceVersion", "测试版")
                        .param("sourceDescription", "学校公开学生手册")
                        .param("applicableScope", "全日制普通本科生")
                        .param("reviewNotice", "仅供本机审核。")
                        .param("knowledgeScope", "PUBLIC_ELECTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PDF"));
    }

    @Test
    void shouldHideUnreadablePdfDetailsBehindUnprocessableResponse() throws Exception {
        when(submissionService.createSubmission(any(), any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("PDF 内容无法读取：内部路径不应暴露"));

        mockMvc.perform(validUpload())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_READ_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "PDF 读取失败，请检查文件后重新上传或提取。"
                ));
    }

    @Test
    void shouldExtractCandidatesAndReturnOnlyCountAndStatus() throws Exception {
        when(submissionService.extractCandidates(SUBMISSION_ID)).thenReturn(
                List.of(candidate(), candidateWithId(UUID.randomUUID()))
        );

        mockMvc.perform(post("/zhku/local-admin/submissions/{id}/extract", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.candidateCount").value(2));
    }

    @Test
    void shouldReturnConflictWhenSubmissionIsNotReadyToPublish() throws Exception {
        when(publisher.publish(SUBMISSION_ID)).thenThrow(
                new IllegalStateException("候选片段尚未全部审核，不能发布")
        );

        mockMvc.perform(post("/zhku/local-admin/submissions/{id}/publish", SUBMISSION_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_NOT_READY"))
                .andExpect(jsonPath("$.message").value(
                        "投稿尚未满足发布条件，请完成全部候选片段审核。"
                ));
    }

    @Test
    void shouldUpdateCandidateWithoutReturningRawText() throws Exception {
        when(reviewService.update(any(), any())).thenReturn(candidate());

        mockMvc.perform(patch("/zhku/local-admin/candidates/{id}", CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startPageNumber":77,"endPageNumber":77,
                                "editedText":"公共选修课最高不得超过 4 学分。",
                                "sectionTitle":"公共选修课程学分上限",
                                "sourceLocator":"PDF 第 77 页，第 10 条"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CANDIDATE_ID.toString()))
                .andExpect(jsonPath("$.editedText")
                        .value("公共选修课最高不得超过 4 学分。"))
                .andExpect(jsonPath("$.rawText").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void shouldApproveCandidateWithReviewNote() throws Exception {
        KnowledgeCandidate approved = new KnowledgeCandidate(
                CANDIDATE_ID, SUBMISSION_ID, 77, 77, "原始文本", "已编辑文本",
                "公共选修课程学分上限", "PDF 第 77 页",
                CandidateReviewStatus.APPROVED, "已人工核验。", null,
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
        when(reviewService.approve(CANDIDATE_ID, "已人工核验。"))
                .thenReturn(approved);

        mockMvc.perform(post("/zhku/local-admin/candidates/{id}/review", CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"note\":\"已人工核验。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));
    }

    @Test
    void shouldSplitCandidateIntoTwoPendingResults() throws Exception {
        when(reviewService.split(any(), any())).thenReturn(
                List.of(candidate(), candidateWithId(UUID.randomUUID()))
        );

        mockMvc.perform(post("/zhku/local-admin/candidates/{id}/split", CANDIDATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstEditedText\":\"第一段\",\"secondEditedText\":\"第二段\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].reviewStatus")
                        .value("PENDING_REVIEW"));
    }

    @Test
    void shouldReturnSafeSubmissionDetail() throws Exception {
        when(repository.findSubmission(SUBMISSION_ID))
                .thenReturn(Optional.of(submission(KnowledgeSubmissionStatus.IN_REVIEW)));
        when(repository.listCandidates(SUBMISSION_ID)).thenReturn(List.of(candidate()));

        mockMvc.perform(get("/zhku/local-admin/submissions/{id}", SUBMISSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("测试学生手册"))
                .andExpect(jsonPath("$.candidates[0].rawText").doesNotExist())
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void shouldReturnSafeSubmissionList() throws Exception {
        when(repository.listSubmissions()).thenReturn(List.of(
                submission(KnowledgeSubmissionStatus.IN_REVIEW)
        ));

        mockMvc.perform(get("/zhku/local-admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SUBMISSION_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
    }

    private static MockHttpServletRequestBuilder validUpload() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "handbook.pdf", MediaType.APPLICATION_PDF_VALUE,
                "%PDF-test".getBytes()
        );
        return multipart("/zhku/local-admin/submissions")
                .file(pdf).param("title", "测试学生手册")
                .param("sourceVersion", "测试版")
                .param("sourceDescription", "学校公开学生手册")
                .param("applicableScope", "全日制普通本科生")
                .param("publicationDate", "2025-09-01")
                .param("publicationDateConfirmed", "true")
                .param("effectiveDate", "2025-09-01")
                .param("effectiveDateConfirmed", "true")
                .param("reviewNotice", "仅供本机审核。")
                .param("knowledgeScope", "PUBLIC_ELECTIVE");
    }

    private static KnowledgeSubmission submission(KnowledgeSubmissionStatus status) {
        return new KnowledgeSubmission(
                SUBMISSION_ID, "STUDENT_HANDBOOK", "PUBLIC_ELECTIVE",
                "测试学生手册", "测试版", "学校公开学生手册", null,
                "全日制普通本科生", LocalDate.of(2025, 9, 1), true,
                LocalDate.of(2025, 9, 1), true, "private-storage-key.pdf",
                "handbook.pdf", "D".repeat(64), status, "仅供本机审核。",
                Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }

    private static KnowledgeCandidate candidate() {
        return candidateWithId(CANDIDATE_ID);
    }

    private static KnowledgeCandidate candidateWithId(UUID id) {
        return new KnowledgeCandidate(
                id, SUBMISSION_ID, 77, 77, "不应从接口返回的原始文本",
                "公共选修课最高不得超过 4 学分。", "公共选修课程学分上限",
                "PDF 第 77 页，第 10 条", CandidateReviewStatus.PENDING_REVIEW,
                null, null, Instant.parse("2026-09-12T00:00:00Z"),
                Instant.parse("2026-09-12T00:00:00Z")
        );
    }
}
