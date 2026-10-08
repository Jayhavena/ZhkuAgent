package com.zhku.knowledge.security;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TrustedActorTest {
    @Test
    void rejectsMissingUserId() {
        assertThatThrownBy(() -> new TrustedActor(null, TrustedActor.Role.USER, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingRole() {
        assertThatThrownBy(() -> new TrustedActor(UUID.randomUUID(), null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeRevision() {
        assertThatThrownBy(() -> new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrincipalWithoutActor() {
        assertThatThrownBy(() -> new PreviewPrincipal(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void principalNameRemainsStableAcrossLoginRevisions() {
        UUID id = UUID.randomUUID();
        var before = new PreviewPrincipal(new TrustedActor(id, TrustedActor.Role.USER, 0));
        var after = new PreviewPrincipal(new TrustedActor(id, TrustedActor.Role.USER, 1));
        assertThat(before.getName()).isEqualTo(id.toString()).isEqualTo(after.getName());
        assertThat(before).isNotEqualTo(after);
        assertThat(new TrustedActor(id, TrustedActor.Role.MAINTAINER, 1).role())
                .isNotEqualTo(TrustedActor.Role.USER);
    }
}
