package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.governance.CandidateReviewStatus;
import com.zhku.knowledge.governance.KnowledgeCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateResponseMappingTest {

    @Test
    void pendingOriginalRemainsSeparateFromSavedTextAndReviewState() {
        var candidate = pending("原文：最高修读4学分。", null);
        var response = ZhkuLocalAdminController.CandidateResponse.from(candidate);

        assertThat(response.rawText()).isEqualTo(candidate.rawText());
        assertThat(response.editedText()).isNull();
        assertThat(response.reviewStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(candidate.editedText()).isNull();
    }

    @Test
    void savedEditsDoNotReplaceOriginalIncludingHtmlAndLineBreaks() {
        String original = "<img src=x onerror=alert(1)>\n原始文本。";
        var response = ZhkuLocalAdminController.CandidateResponse.from(
                pending(original, "人工保存的正文"));

        assertThat(response.rawText()).isEqualTo(original);
        assertThat(response.editedText()).isEqualTo("人工保存的正文");
        assertThat(response.reviewStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void blankPhysicalPagesRemainVisibleToReviewer() {
        for (String blank : new String[]{"", "  \n  "}) {
            var response = ZhkuLocalAdminController.CandidateResponse.from(pending(blank, null));
            assertThat(response.rawText()).isEqualTo(blank);
            assertThat(response.startPageNumber()).isEqualTo(2);
            assertThat(response.endPageNumber()).isEqualTo(3);
            assertThat(response.reviewStatus()).isEqualTo("PENDING_REVIEW");
        }
    }

    private static KnowledgeCandidate pending(String raw, String edited) {
        var now = Instant.parse("2026-10-07T00:00:00Z");
        return new KnowledgeCandidate(UUID.randomUUID(), UUID.randomUUID(), 2, 3,
                raw, edited, null, null, CandidateReviewStatus.PENDING_REVIEW,
                null, null, now, now);
    }
}
