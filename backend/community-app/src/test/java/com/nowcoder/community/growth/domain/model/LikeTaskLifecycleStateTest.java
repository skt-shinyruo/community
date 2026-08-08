package com.nowcoder.community.growth.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState.Transition.ACTIVATED;
import static com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState.Transition.ADVANCED;
import static com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState.Transition.DEACTIVATED;
import static com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState.Transition.IGNORED;
import static com.nowcoder.community.growth.domain.model.LikeTaskLifecycleState.Transition.REPLACED;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class LikeTaskLifecycleStateTest {

    private static final UUID FIRST_INSTANCE = uuid(701);
    private static final UUID SECOND_INSTANCE = uuid(702);

    @Test
    void inactiveTombstoneShouldRejectAnOlderCreate() {
        LikeTaskLifecycleState removed = state(FIRST_INSTANCE, 2L, false, "removed");

        assertThat(LikeTaskLifecycleState.decide(
                removed,
                state(FIRST_INSTANCE, 1L, true, "created")
        )).isEqualTo(IGNORED);
    }

    @Test
    void newerCreateForAnotherInstanceShouldReplaceTheActiveContribution() {
        LikeTaskLifecycleState first = state(FIRST_INSTANCE, 1L, true, "first-created");

        assertThat(LikeTaskLifecycleState.decide(
                first,
                state(SECOND_INSTANCE, 3L, true, "second-created")
        )).isEqualTo(REPLACED);
    }

    @Test
    void olderRemovalCannotDeactivateTheNewerInstance() {
        LikeTaskLifecycleState second = state(SECOND_INSTANCE, 3L, true, "second-created");

        assertThat(LikeTaskLifecycleState.decide(
                second,
                state(FIRST_INSTANCE, 2L, false, "first-removed")
        )).isEqualTo(IGNORED);
    }

    @Test
    void legacyRemovalCannotDeactivateAKnownLifecycleEvenWithAHigherSourceVersion() {
        LikeTaskLifecycleState versioned = state(FIRST_INSTANCE, 20L, true, "versioned-created");

        assertThat(LikeTaskLifecycleState.decide(
                versioned,
                state(null, 21L, false, "legacy-removed")
        )).isEqualTo(IGNORED);
    }

    @Test
    void legacyCreateCannotReplaceAKnownLifecycleEvenWithAHigherSourceVersion() {
        LikeTaskLifecycleState versioned = state(FIRST_INSTANCE, 20L, true, "versioned-created");

        assertThat(LikeTaskLifecycleState.decide(
                versioned,
                state(null, 21L, true, "legacy-created")
        )).isEqualTo(IGNORED);
    }

    @Test
    void activeInstanceShouldWinALegacyVersionTieAcrossKnownLifecycles() {
        LikeTaskLifecycleState removed = state(FIRST_INSTANCE, 20L, false, "first-removed");
        LikeTaskLifecycleState created = state(SECOND_INSTANCE, 20L, true, "second-created");

        assertThat(LikeTaskLifecycleState.decide(removed, created)).isEqualTo(ACTIVATED);
        assertThat(LikeTaskLifecycleState.decide(created, removed)).isEqualTo(IGNORED);
    }

    @Test
    void sameInstanceShouldUseVersionAndRemovalTieBreaking() {
        LikeTaskLifecycleState created = state(FIRST_INSTANCE, 20L, true, "created");

        assertThat(LikeTaskLifecycleState.decide(
                created,
                state(FIRST_INSTANCE, 21L, true, "duplicate-created")
        )).isEqualTo(ADVANCED);
        assertThat(LikeTaskLifecycleState.decide(
                created,
                state(FIRST_INSTANCE, 20L, false, "removed")
        )).isEqualTo(DEACTIVATED);
    }

    @Test
    void instanceIdentityShouldRemainOpaque() {
        UUID historicalUuidV1 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        LikeTaskLifecycleState historical = state(historicalUuidV1, 20L, true, "created");

        assertThat(historical.relationInstanceId()).isEqualTo(historicalUuidV1);
        assertThat(LikeTaskLifecycleState.decide(
                historical,
                state(historicalUuidV1, 21L, false, "removed")
        )).isEqualTo(DEACTIVATED);
    }

    private static LikeTaskLifecycleState state(
            UUID relationInstanceId,
            long sourceVersion,
            boolean active,
            String eventId
    ) {
        return new LikeTaskLifecycleState(
                uuid(2),
                "like:" + uuid(9) + ":3:" + uuid(100),
                relationInstanceId,
                sourceVersion,
                active,
                eventId
        );
    }
}
