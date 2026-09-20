package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.feedback.FeedbackAggregate;
import com.zhku.knowledge.feedback.FeedbackAggregateQueryService;
import com.zhku.knowledge.feedback.FeedbackReason;
import com.zhku.knowledge.feedback.FeedbackRetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本机管理员只能审核不可识别聚合，不能读取或导出单条匿名反馈。 */
class FeedbackAdminControllerTest {

    private FeedbackAggregateQueryService aggregateQueryService;
    private FeedbackRetentionService retentionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aggregateQueryService = mock(FeedbackAggregateQueryService.class);
        retentionService = mock(FeedbackRetentionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FeedbackAdminController(
                aggregateQueryService, retentionService
        )).build();
    }

    @Test
    void shouldReturnOnlyAggregateFieldsForAdministratorReview() throws Exception {
        when(aggregateQueryService.listAggregates()).thenReturn(List.of(
                new FeedbackAggregate(
                        "course-selection", "2026-1", FeedbackReason.OUTDATED,
                        LocalDate.of(2026, 9, 20), 4L
                )
        ));

        mockMvc.perform(get("/zhku/local-admin/feedback/aggregates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].guideIdentifier").value("course-selection"))
                .andExpect(jsonPath("$[0].guideVersion").value("2026-1"))
                .andExpect(jsonPath("$[0].reason").value("OUTDATED"))
                .andExpect(jsonPath("$[0].feedbackDate").value("2026-09-20"))
                .andExpect(jsonPath("$[0].count").value(4))
                .andExpect(jsonPath("$[0].anonymousSessionHash").doesNotExist())
                .andExpect(jsonPath("$[0].dedupeKey").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    void shouldRunRetentionOnlyWhenLocalAdministratorExplicitlyRequestsIt()
            throws Exception {
        when(retentionService.aggregateExpired()).thenReturn(3);

        mockMvc.perform(post("/zhku/local-admin/feedback/retention"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregatedEventCount").value(3))
                .andExpect(jsonPath("$.published").doesNotExist())
                .andExpect(jsonPath("$.indexRunId").doesNotExist())
                .andExpect(jsonPath("$.candidateId").doesNotExist());

        verify(retentionService).aggregateExpired();
    }
}
