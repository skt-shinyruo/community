package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.projection.RoomMembershipEntry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MembershipProjectionServiceTest {

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
            long occurredAtEpochMillis
    ) {
        return new RoomMembershipEntry(roomId, userId, version, occurredAtEpochMillis);
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
