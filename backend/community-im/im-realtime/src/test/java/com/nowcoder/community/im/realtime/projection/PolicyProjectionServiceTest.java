package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyProjectionServiceTest {

    @Test
    void snapshotThenNewerUserPolicyDeltaShouldIgnoreOutOfOrderAndDuplicateEvents() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 100L), allowPolicy(user(2), 100L)),
                        100L
                )
        ));
        when(snapshotClient.fetchBlockRelationSnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 100L)
        ));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();

        service.applyUserMessagingPolicyChanged(policyEvent(user(1), true, false, 200L));
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        service.applyUserMessagingPolicyChanged(policyEvent(user(1), false, true, 150L));
        service.applyUserMessagingPolicyChanged(policyEvent(user(1), false, true, 200L));

        PolicyDecision decision = service.canSendPrivateMessage(user(1), user(2));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("policy_denied");
    }

    @Test
    void olderSnapshotAfterBlockDeltaShouldNotRollbackButNewerSnapshotCanRemoveBlock() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenReturn(
                Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 100L), allowPolicy(user(2), 100L)),
                        100L
                )),
                Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 300L), allowPolicy(user(2), 300L)),
                        300L
                ))
        );
        when(snapshotClient.fetchBlockRelationSnapshot()).thenReturn(
                Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 100L)),
                Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 300L))
        );
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        service.applyUserMessagingPolicyChanged(policyEvent(user(1), false, true, 50L));
        service.applyUserMessagingPolicyChanged(policyEvent(user(2), false, true, 50L));
        service.applyUserBlockRelationChanged(blockEvent(user(1), user(2), true, 200L));
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    @Test
    void blockRelationDeltaShouldIgnoreOutOfOrderAndDuplicateEvents() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 100L), allowPolicy(user(2), 100L)),
                        100L
                )
        ));
        when(snapshotClient.fetchBlockRelationSnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 100L)
        ));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.applyUserBlockRelationChanged(blockEvent(user(1), user(2), true, 200L))).isTrue();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        assertThat(service.applyUserBlockRelationChanged(blockEvent(user(1), user(2), false, 150L))).isFalse();
        assertThat(service.applyUserBlockRelationChanged(blockEvent(user(1), user(2), true, 200L))).isFalse();

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();
    }

    @Test
    void snapshotWatermarkShouldProtectLegacyPolicyEntriesWithoutExplicitVersion() {
        long snapshotWatermark = ProjectionVersions.snapshotHighWatermarkFromEpochMillis(300L);
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(
                                legacyAllowPolicy(user(1), 100L),
                                legacyAllowPolicy(user(2), 100L)
                        ),
                        snapshotWatermark
                )
        ));
        when(snapshotClient.fetchBlockRelationSnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), snapshotWatermark)
        ));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();

        service.applyUserMessagingPolicyChanged(policyEvent(
                user(1),
                true,
                false,
                200L,
                ProjectionVersions.fromEpochMillis(200L)
        ));

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    private static UserMessagingPolicyEntry allowPolicy(UUID userId, long version) {
        return new UserMessagingPolicyEntry(
                userId,
                true,
                false,
                false,
                null,
                null,
                true,
                version,
                version
        );
    }

    private static UserMessagingPolicyEntry legacyAllowPolicy(UUID userId, long occurredAtEpochMillis) {
        return new UserMessagingPolicyEntry(
                userId,
                true,
                false,
                false,
                null,
                null,
                true,
                null,
                occurredAtEpochMillis
        );
    }

    private static UserMessagingPolicyChanged policyEvent(
            UUID userId,
            boolean muted,
            boolean canSendPrivate,
            long version
    ) {
        return policyEvent(userId, muted, canSendPrivate, version, version);
    }

    private static UserMessagingPolicyChanged policyEvent(
            UUID userId,
            boolean muted,
            boolean canSendPrivate,
            long occurredAtEpochMillis,
            long version
    ) {
        return new UserMessagingPolicyChanged(
                "evt-policy-" + version,
                userId,
                true,
                false,
                muted,
                muted ? occurredAtEpochMillis + 60_000 : null,
                null,
                canSendPrivate,
                occurredAtEpochMillis,
                version
        );
    }

    private static UserBlockRelationChanged blockEvent(UUID blockerUserId, UUID blockedUserId, boolean active, long version) {
        return new UserBlockRelationChanged(
                "evt-block-" + version,
                blockerUserId,
                blockedUserId,
                active,
                version,
                version
        );
    }

    private static UUID user(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
