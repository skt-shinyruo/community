package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.im.common.projection.UserBlockRelationEntry;
import com.nowcoder.community.im.common.projection.UserMessagingPolicyEntry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyProjectionServiceTest {

    @Test
    void invalidPolicySnapshotEntryShouldFailAndPreserveState() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                        10L
                )))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(new UserMessagingPolicyEntry(
                                null, true, false, false, null, null, true, 11L, 9_100L)),
                        11L
                )));
        when(snapshotClient.fetchBlockRelationSnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 10L)))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 11L)));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();

        StepVerifier.create(service.refreshNow())
                .expectErrorMatches(error -> error instanceof IllegalStateException)
                .verify();

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    @Test
    void invalidBlockSnapshotEntryShouldFailAndPreserveState() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                        10L
                )))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 12L, 9_100L), allowPolicy(user(2), 12L, 9_100L)),
                        12L
                )));
        when(snapshotClient.fetchBlockRelationSnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 10L)))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(
                        List.of(new UserBlockRelationEntry(user(1), null, true, 11L, 9_100L)),
                        11L
                )));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();

        StepVerifier.create(service.refreshNow())
                .expectErrorMatches(error -> error instanceof IllegalStateException)
                .verify();

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    @Test
    void malformedPolicyEventShouldThrowInsteadOfSilentAck() {
        PolicyProjectionService service = new PolicyProjectionService(mock(PolicySnapshotClient.class));

        assertThatThrownBy(() -> service.applyUserMessagingPolicyChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUserMessagingPolicyChanged(policyEvent(null, false, true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUserMessagingPolicyChanged(new UserMessagingPolicyChanged(
                " ", user(1), true, false, false, null, null, true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedBlockEventShouldThrowInsteadOfSilentAck() {
        PolicyProjectionService service = new PolicyProjectionService(mock(PolicySnapshotClient.class));

        assertThatThrownBy(() -> service.applyUserBlockRelationChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUserBlockRelationChanged(blockEvent(null, user(2), true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUserBlockRelationChanged(blockEvent(user(1), null, true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyUserBlockRelationChanged(new UserBlockRelationChanged(
                " ", user(1), user(2), true, 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canSendPrivateMessageShouldNeverMixRefreshGenerations() throws Exception {
        AtomicInteger generation = new AtomicInteger(1);
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenAnswer(invocation -> {
            int n = generation.get();
            List<UserMessagingPolicyEntry> entries = n % 2 == 1
                    ? List.of(allowPolicy(user(1), n, null), allowPolicy(user(2), n, null))
                    : List.of();
            return Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(entries, n));
        });
        when(snapshotClient.fetchBlockRelationSnapshot()).thenAnswer(invocation -> {
            int n = generation.get();
            List<UserBlockRelationEntry> entries = n % 2 == 1
                    ? List.of()
                    : List.of(blockEntry(user(1), user(2), true, n, null));
            return Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(entries, n));
        });
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);
        service.refreshNow().block();

        int refreshes = 300;
        int samples = 100_000;
        ConcurrentLinkedQueue<PolicyDecision> violations = new ConcurrentLinkedQueue<>();
        AtomicBoolean writerDone = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = executor.submit(() -> {
                await(start);
                for (int i = 2; i < refreshes + 2; i++) {
                    generation.set(i);
                    service.refreshNow().block();
                }
                writerDone.set(true);
            });
            Future<?> reader = executor.submit(() -> {
                await(start);
                for (int i = 0; i < samples && !writerDone.get(); i++) {
                    PolicyDecision decision = service.canSendPrivateMessage(user(1), user(2));
                    if (!decision.allowed() && !"发送方不存在".equals(decision.message())) {
                        violations.add(decision);
                    }
                }
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void higherPolicyVersionShouldWinDespiteEarlierTimestamp() {
        PolicySnapshotClient snapshotClient = snapshotClient(
                List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                10L,
                List.of(),
                10L
        );
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();

        service.applyUserMessagingPolicyChanged(policyEvent(user(1), true, false, 100L, 11L));
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        service.applyUserMessagingPolicyChanged(policyEvent(user(1), false, true, 10_000L, 10L));
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();
    }

    @Test
    void higherBlockVersionShouldWinDespiteEarlierTimestamp() {
        PolicySnapshotClient snapshotClient = snapshotClient(
                List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                10L,
                List.of(),
                10L
        );
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.applyUserBlockRelationChanged(blockEvent(
                user(1), user(2), true, 100L, 11L
        ))).isTrue();

        assertThat(service.applyUserBlockRelationChanged(blockEvent(
                user(1), user(2), false, 10_000L, 10L
        ))).isFalse();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();
    }

    @Test
    void newerSnapshotWatermarkShouldRemoveBlockDelta() {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                        10L
                )))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedUserPolicySnapshot(
                        List.of(allowPolicy(user(1), 12L, 100L), allowPolicy(user(2), 12L, 100L)),
                        12L
                )));
        when(snapshotClient.fetchBlockRelationSnapshot())
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 10L)))
                .thenReturn(Mono.just(new PolicySnapshotClient.FetchedBlockRelationSnapshot(List.of(), 12L)));
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.applyUserBlockRelationChanged(blockEvent(
                user(1), user(2), true, 500L, 11L
        ))).isTrue();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    @Test
    void policySnapshotEntryVersionShouldBeMergedWithWatermark() {
        PolicySnapshotClient snapshotClient = snapshotClient(
                List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                11L,
                List.of(),
                0L
        );
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        service.applyUserMessagingPolicyChanged(policyEvent(user(1), true, false, 10_000L, 11L));

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isTrue();
    }

    @Test
    void blockSnapshotEntryVersionShouldBeMergedWithWatermark() {
        PolicySnapshotClient snapshotClient = snapshotClient(
                List.of(allowPolicy(user(1), 10L, 9_000L), allowPolicy(user(2), 10L, 9_000L)),
                10L,
                List.of(blockEntry(user(1), user(2), true, 10L, 9_000L)),
                11L
        );
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.applyUserBlockRelationChanged(blockEvent(
                user(1), user(2), false, 10_000L, 11L
        ))).isFalse();

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();
    }

    @Test
    void explicitZeroWatermarksShouldReplaceEmptyInitialSnapshots() {
        PolicySnapshotClient snapshotClient = snapshotClient(List.of(), 0L, List.of(), 0L);
        PolicyProjectionService service = new PolicyProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();

        assertThat(service.canSendPrivateMessage(user(1), user(2)).allowed()).isFalse();
    }

    private static PolicySnapshotClient snapshotClient(
            List<UserMessagingPolicyEntry> policies,
            long policyWatermark,
            List<UserBlockRelationEntry> blocks,
            long blockWatermark
    ) {
        PolicySnapshotClient snapshotClient = mock(PolicySnapshotClient.class);
        when(snapshotClient.fetchUserPolicySnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedUserPolicySnapshot(policies, policyWatermark)
        ));
        when(snapshotClient.fetchBlockRelationSnapshot()).thenReturn(Mono.just(
                new PolicySnapshotClient.FetchedBlockRelationSnapshot(blocks, blockWatermark)
        ));
        return snapshotClient;
    }

    private static UserMessagingPolicyEntry allowPolicy(
            UUID userId,
            long version,
            Long occurredAtEpochMillis
    ) {
        return new UserMessagingPolicyEntry(
                userId,
                true,
                false,
                false,
                null,
                null,
                true,
                version,
                occurredAtEpochMillis
        );
    }

    private static UserBlockRelationEntry blockEntry(
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            long version,
            Long occurredAtEpochMillis
    ) {
        return new UserBlockRelationEntry(
                blockerUserId,
                blockedUserId,
                active,
                version,
                occurredAtEpochMillis
        );
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

    private static UserBlockRelationChanged blockEvent(
            UUID blockerUserId,
            UUID blockedUserId,
            boolean active,
            long occurredAtEpochMillis,
            long version
    ) {
        return new UserBlockRelationChanged(
                "evt-block-" + version,
                blockerUserId,
                blockedUserId,
                active,
                occurredAtEpochMillis,
                version
        );
    }

    private static UUID user(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
