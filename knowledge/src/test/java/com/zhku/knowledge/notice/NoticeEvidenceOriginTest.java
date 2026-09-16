package com.zhku.knowledge.notice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeEvidenceOriginTest {

    @Test
    void shouldAllowReviewedInternalOfficialMaterialWithoutInventingPublicUrl() {
        NoticeEvidenceOrigin origin = NoticeEvidenceOrigin.internalOfficial(
                "教务部",
                "教务系统正式下发",
                "2026-2027-1-选课通知"
        );

        assertThat(origin.channel()).isEqualTo(
                NoticeEvidenceChannel.INTERNAL_OFFICIAL
        );
        assertThat(origin.publicUrl()).isNull();
        assertThat(origin.studentCitationLabel()).contains(
                "已审核校内正式通知", "教务部", "2026-2027-1-选课通知"
        );
    }

    @Test
    void shouldRejectInternalMaterialWithoutTraceableIssuingMetadata() {
        assertThatThrownBy(() -> NoticeEvidenceOrigin.internalOfficial(
                "", "教务系统正式下发", "2026-2027-1-选课通知"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("校内正式材料必须记录签发单位");
    }
}
