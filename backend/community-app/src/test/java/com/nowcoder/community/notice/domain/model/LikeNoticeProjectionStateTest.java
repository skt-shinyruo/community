package com.nowcoder.community.notice.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.ACTIVATED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.ADVANCED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.DEACTIVATED;
import static com.nowcoder.community.notice.domain.model.LikeNoticeProjectionState.Transition.IGNORED;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class LikeNoticeProjectionStateTest {

    private static final UUID FIRST_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID SECOND_LIFECYCLE =
            UUID.fromString("00000000-0000-7000-8000-000000000002");
    private static final UUID HISTORICAL_UUID_V1 =
            UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    @Test
    void tombstoneShouldRejectAnOlderCreateFromTheSameLifecycle() {
        LikeNoticeProjectionState removed = state(FIRST_LIFECYCLE, 20L, false, "removed");

        assertThat(LikeNoticeProjectionState.decide(
                removed, state(FIRST_LIFECYCLE, 10L, true, "created")))
                .isEqualTo(IGNORED);
    }

    @Test
    void durableRelationVersionShouldOrderDifferentLifecyclesWithoutComparingUuidValues() {
        LikeNoticeProjectionState oldLifecycle = state(FIRST_LIFECYCLE, 100L, true, "old-created");
        LikeNoticeProjectionState newLifecycle = state(SECOND_LIFECYCLE, 101L, true, "new-created");

        assertThat(LikeNoticeProjectionState.decide(oldLifecycle, newLifecycle)).isEqualTo(ACTIVATED);
        assertThat(LikeNoticeProjectionState.decide(
                newLifecycle, state(FIRST_LIFECYCLE, 99L, false, "old-removed")))
                .isEqualTo(IGNORED);
    }

    @Test
    void sourceVersionShouldFenceEventsWithinOneLifecycle() {
        LikeNoticeProjectionState created = state(FIRST_LIFECYCLE, 20L, true, "created");

        assertThat(LikeNoticeProjectionState.decide(
                created, state(FIRST_LIFECYCLE, 19L, false, "stale-remove")))
                .isEqualTo(IGNORED);
        assertThat(LikeNoticeProjectionState.decide(
                created, state(FIRST_LIFECYCLE, 20L, false, "remove")))
                .isEqualTo(DEACTIVATED);
        assertThat(LikeNoticeProjectionState.decide(
                created, state(FIRST_LIFECYCLE, 21L, true, "duplicate-create")))
                .isEqualTo(ADVANCED);
    }

    @Test
    void activeLifecycleShouldWinALegacyVersionTieAcrossKnownInstances() {
        LikeNoticeProjectionState firstRemoved = state(FIRST_LIFECYCLE, 20L, false, "first-removed");
        LikeNoticeProjectionState secondCreated = state(SECOND_LIFECYCLE, 20L, true, "second-created");

        assertThat(LikeNoticeProjectionState.decide(firstRemoved, secondCreated)).isEqualTo(ACTIVATED);
        assertThat(LikeNoticeProjectionState.decide(secondCreated, firstRemoved)).isEqualTo(IGNORED);
    }

    @Test
    void legacyEventsShouldFallBackToSourceVersionOrdering() {
        LikeNoticeProjectionState removed = state(null, 20L, false, "legacy-removed");

        assertThat(LikeNoticeProjectionState.decide(
                removed, state(null, 10L, true, "legacy-stale-created")))
                .isEqualTo(IGNORED);
        assertThat(LikeNoticeProjectionState.decide(
                removed, state(null, 21L, true, "legacy-created")))
                .isEqualTo(ACTIVATED);
    }

    @Test
    void historicalNonV7RelationInstanceShouldRemainAnOpaqueIdentity() {
        LikeNoticeProjectionState historical = state(HISTORICAL_UUID_V1, 20L, true, "historical-created");

        assertThat(historical.relationInstanceId()).isEqualTo(HISTORICAL_UUID_V1);
        assertThat(LikeNoticeProjectionState.decide(
                historical, state(HISTORICAL_UUID_V1, 21L, false, "historical-removed")))
                .isEqualTo(DEACTIVATED);
    }

    @Test
    void durableVersionRangeShouldSupersedeLegacyEpochMillisecondState() {
        LikeNoticeProjectionState legacy = state(null, 1_800_000_000_000L, false, "legacy-removed");
        LikeNoticeProjectionState durable = state(
                SECOND_LIFECYCLE,
                4_611_686_018_427_387_905L,
                true,
                "durable-created"
        );

        assertThat(LikeNoticeProjectionState.decide(legacy, durable)).isEqualTo(ACTIVATED);
    }

    private static LikeNoticeProjectionState state(
            UUID relationInstanceId,
            long sourceVersion,
            boolean active,
            String eventId
    ) {
        return new LikeNoticeProjectionState(
                uuid(9),
                "like:" + uuid(1) + ":3:" + uuid(100),
                relationInstanceId,
                sourceVersion,
                active,
                eventId
        );
    }
}
