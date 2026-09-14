package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
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

class MembershipProjectionServiceTest {

    @Test
    void snapshotWithInvalidEntryShouldFailAndPreserveCurrentState() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot())
                .thenReturn(Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(membershipEntry(room(1), user(1), 10L, 9_000L)),
                        10L
                )))
                .thenReturn(Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(new RoomMembershipEntry(null, user(2), 11L, 9_100L)),
                        11L
                )));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        StepVerifier.create(service.refreshNow())
                .expectErrorMatches(error -> error instanceof IllegalStateException)
                .verify();

        assertThat(service.isMember(room(1), user(1))).isTrue();
        assertThat(service.roomIdsForUser(user(1))).containsExactly(room(1));
        assertThat(service.roomIdsForUser(user(2))).isEmpty();
    }

    @Test
    void malformedRoomMemberEventShouldThrowInsteadOfSilentAck() {
        MembershipProjectionService service = new MembershipProjectionService(mock(MembershipSnapshotClient.class));

        assertThatThrownBy(() -> service.applyRoomMemberChanged(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyRoomMemberChanged(roomMemberEvent(null, user(1), "JOINED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyRoomMemberChanged(roomMemberEvent(room(1), null, "JOINED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "BANNED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyRoomMemberChanged(new RoomMemberChanged(
                " ", room(1), user(1), "JOINED", 100L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refreshShouldPublishEachGenerationAtomicallyToConcurrentReaders() throws Exception {
        AtomicInteger fetchCount = new AtomicInteger();
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenAnswer(invocation -> {
            int n = fetchCount.incrementAndGet();
            List<RoomMembershipEntry> entries = n % 2 == 1
                    ? List.of(
                            membershipEntry(room(91), user(9), n, null),
                            membershipEntry(room(92), user(9), n, null))
                    : List.of(
                            membershipEntry(room(93), user(9), n, null),
                            membershipEntry(room(94), user(9), n, null));
            return Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(entries, n));
        });
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);
        service.refreshNow().block();

        Set<UUID> generationOdd = Set.of(room(91), room(92));
        Set<UUID> generationEven = Set.of(room(93), room(94));
        int refreshes = 300;
        int samples = 100_000;
        ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();
        AtomicBoolean writerDone = new AtomicBoolean(false);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = executor.submit(() -> {
                await(start);
                for (int i = 0; i < refreshes; i++) {
                    service.refreshNow().block();
                }
                writerDone.set(true);
            });
            Future<?> reader = executor.submit(() -> {
                await(start);
                for (int i = 0; i < samples && !writerDone.get(); i++) {
                    Set<UUID> rooms = service.roomIdsForUser(user(9));
                    if (!generationOdd.equals(rooms) && !generationEven.equals(rooms)) {
                        violations.add("partial generation: rooms=" + rooms);
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
    void higherVersionShouldWinDespiteEarlierTimestamp() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(Mono.just(
                new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(membershipEntry(room(1), user(1), 10L, 9_000L)),
                        10L
                )
        ));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(
                room(1), user(1), "LEFT", 100L, 11L
        ))).isTrue();
        assertThat(service.isMember(room(1), user(1))).isFalse();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(
                room(1), user(1), "JOINED", 10_000L, 10L
        ))).isFalse();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    @Test
    void snapshotWatermarkShouldOrderRemovalAgainstDelta() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot())
                .thenReturn(Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(List.of(), 10L)))
                .thenReturn(Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(List.of(), 12L)));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "JOINED", 500L, 11L));
        assertThat(service.isMember(room(1), user(1))).isTrue();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    @Test
    void snapshotEntryVersionShouldBeMergedWithWatermark() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(Mono.just(
                new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(membershipEntry(room(1), user(1), 10L, 9_000L)),
                        11L
                )
        ));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(
                room(1), user(1), "LEFT", 10_000L, 11L
        ))).isFalse();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(
                room(1), user(1), "LEFT", 100L, 12L
        ))).isTrue();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    @Test
    void explicitZeroWatermarkShouldReplaceEmptyInitialSnapshot() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(Mono.just(
                new MembershipSnapshotClient.FetchedMembershipSnapshot(List.of(), 0L)
        ));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();

        assertThat(service.roomIdsForUser(user(1))).isEmpty();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    private static RoomMembershipEntry membershipEntry(
            UUID roomId,
            UUID userId,
            long version,
            Long occurredAtEpochMillis
    ) {
        return new RoomMembershipEntry(roomId, userId, version, occurredAtEpochMillis);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static RoomMemberChanged roomMemberEvent(
            UUID roomId,
            UUID userId,
            String action,
            long occurredAtEpochMillis,
            long version
    ) {
        return new RoomMemberChanged(
                "evt-room-member-" + version,
                roomId,
                userId,
                action,
                occurredAtEpochMillis,
                version
        );
    }

    private static UUID room(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private static UUID user(long suffix) {
        return UUID.fromString("00000000-0000-7001-8000-" + String.format("%012x", suffix));
    }
}
