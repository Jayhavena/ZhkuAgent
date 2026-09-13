package com.yupi.yuaiagent.zhku.knowledge.governance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSubmissionStatusTest {

    @Test
    void shouldAllowOnlyExpectedSubmissionTransitions() {
        assertThat(KnowledgeSubmissionStatus.DRAFT.canTransitionTo(
                KnowledgeSubmissionStatus.EXTRACTED
        )).isTrue();
        assertThat(KnowledgeSubmissionStatus.EXTRACTED.canTransitionTo(
                KnowledgeSubmissionStatus.IN_REVIEW
        )).isTrue();
        assertThat(KnowledgeSubmissionStatus.EXTRACTED.canTransitionTo(
                KnowledgeSubmissionStatus.REJECTED
        )).isTrue();
        assertThat(KnowledgeSubmissionStatus.IN_REVIEW.canTransitionTo(
                KnowledgeSubmissionStatus.PUBLISHED
        )).isTrue();
        assertThat(KnowledgeSubmissionStatus.PUBLISHED.canTransitionTo(
                KnowledgeSubmissionStatus.ARCHIVED
        )).isTrue();
        assertThat(KnowledgeSubmissionStatus.ARCHIVED.canTransitionTo(
                KnowledgeSubmissionStatus.IN_REVIEW
        )).isFalse();
        assertThat(KnowledgeSubmissionStatus.REJECTED.canTransitionTo(
                KnowledgeSubmissionStatus.EXTRACTED
        )).isFalse();
    }
}
