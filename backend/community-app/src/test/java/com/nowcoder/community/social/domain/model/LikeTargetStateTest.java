package com.nowcoder.community.social.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static org.assertj.core.api.Assertions.assertThat;

class LikeTargetStateTest {

    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-7000-8000-000000000601");
    private static final Instant FIRST_DELETED_AT = Instant.parse("2026-07-15T08:30:00Z");

    @Test
    void activeTargetShouldTransitionToDeletedExactlyOnce() {
        LikeTargetState active = LikeTargetState.active(POST, TARGET_ID);

        LikeTargetState deleted = active.applyDeletion("content:post-deleted:601", 10L, FIRST_DELETED_AT);

        assertThat(active.isActive()).isTrue();
        assertThat(deleted).isNotSameAs(active);
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.sourceEventId()).isEqualTo("content:post-deleted:601");
        assertThat(deleted.sourceVersion()).isEqualTo(10L);
        assertThat(deleted.deletedAt()).isEqualTo(FIRST_DELETED_AT);
    }

    @Test
    void duplicateAndOlderDeletionFactsShouldBeNoOps() {
        LikeTargetState deleted = LikeTargetState.active(POST, TARGET_ID)
                .applyDeletion("content:post-deleted:601", 10L, FIRST_DELETED_AT);

        LikeTargetState duplicate = deleted.applyDeletion(
                "content:post-deleted:601",
                10L,
                FIRST_DELETED_AT
        );
        LikeTargetState stale = deleted.applyDeletion(
                "content:post-deleted:stale",
                9L,
                FIRST_DELETED_AT.minusSeconds(30)
        );

        assertThat(duplicate).isSameAs(deleted);
        assertThat(stale).isSameAs(deleted);
        assertThat(deleted.sourceEventId()).isEqualTo("content:post-deleted:601");
        assertThat(deleted.sourceVersion()).isEqualTo(10L);
        assertThat(deleted.deletedAt()).isEqualTo(FIRST_DELETED_AT);
    }

    @Test
    void newerDeletionFactMayAdvanceMetadataButMustNeverReactivateTarget() {
        LikeTargetState deleted = LikeTargetState.active(POST, TARGET_ID)
                .applyDeletion("content:post-deleted:601", 10L, FIRST_DELETED_AT);
        Instant newerDeletedAt = FIRST_DELETED_AT.plusSeconds(20);

        LikeTargetState advanced = deleted.applyDeletion(
                "content:post-deleted:601-replayed",
                11L,
                newerDeletedAt
        );

        assertThat(advanced).isNotSameAs(deleted);
        assertThat(advanced.isDeleted()).isTrue();
        assertThat(advanced.isActive()).isFalse();
        assertThat(advanced.sourceVersion()).isEqualTo(11L);
        assertThat(advanced.sourceEventId()).isEqualTo("content:post-deleted:601-replayed");
        assertThat(advanced.deletedAt()).isEqualTo(newerDeletedAt);
    }
}
