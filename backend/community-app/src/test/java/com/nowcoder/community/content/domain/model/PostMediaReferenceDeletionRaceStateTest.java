package com.nowcoder.community.content.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class PostMediaReferenceDeletionRaceStateTest {

    @Test
    void deleteIntentShouldSupersedePendingBindWithANewerReleaseVersion() {
        Date bindRequestedAt = Date.from(Instant.parse("2026-07-15T09:00:00Z"));
        Date deletedAt = Date.from(Instant.parse("2026-07-15T09:01:00Z"));
        PostMediaReferenceState pendingBind = new PostMediaReferenceState(
                PostMediaReferenceStatus.BIND_PENDING,
                4L,
                bindRequestedAt
        );

        PostMediaReferenceState release = pendingBind.requestRelease(deletedAt);

        assertThat(release.status()).isEqualTo(PostMediaReferenceStatus.RELEASE_PENDING);
        assertThat(release.operationVersion()).isEqualTo(5L);
        assertThat(release.updatedAt()).isEqualTo(deletedAt);
    }
}
