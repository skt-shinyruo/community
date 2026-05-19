package com.nowcoder.community.im.realtime.projection;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.common.projection.ProjectionVersions;
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
    void snapshotThenNewerLeaveDeltaShouldIgnoreOutOfOrderAndDuplicateJoin() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(Mono.just(
                new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(new RoomMembershipEntry(room(1), user(1), 100L, 100L)),
                        100L
                )
        ));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "LEFT", 200L))).isTrue();
        assertThat(service.isMember(room(1), user(1))).isFalse();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "JOINED", 150L))).isFalse();
        assertThat(service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "LEFT", 200L))).isFalse();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    @Test
    void eventThenOlderSnapshotShouldNotRollbackButNewerSnapshotCanReplaceState() {
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(
                Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(List.of(), 100L)),
                Mono.just(new MembershipSnapshotClient.FetchedMembershipSnapshot(List.of(), 300L))
        );
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        service.applyRoomMemberChanged(roomMemberEvent(room(1), user(1), "JOINED", 200L));
        assertThat(service.isMember(room(1), user(1))).isTrue();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isFalse();
    }

    @Test
    void snapshotWatermarkShouldProtectLegacyMembershipEntriesWithoutExplicitVersion() {
        long snapshotWatermark = ProjectionVersions.snapshotHighWatermarkFromEpochMillis(300L);
        MembershipSnapshotClient snapshotClient = mock(MembershipSnapshotClient.class);
        when(snapshotClient.fetchSnapshot()).thenReturn(Mono.just(
                new MembershipSnapshotClient.FetchedMembershipSnapshot(
                        List.of(new RoomMembershipEntry(room(1), user(1), null, 100L)),
                        snapshotWatermark
                )
        ));
        MembershipProjectionService service = new MembershipProjectionService(snapshotClient);

        StepVerifier.create(service.refreshNow()).verifyComplete();
        assertThat(service.isMember(room(1), user(1))).isTrue();

        assertThat(service.applyRoomMemberChanged(roomMemberEvent(
                room(1),
                user(1),
                "LEFT",
                200L,
                ProjectionVersions.fromEpochMillis(200L)
        ))).isFalse();
        assertThat(service.isMember(room(1), user(1))).isTrue();
    }

    private static RoomMemberChanged roomMemberEvent(UUID roomId, UUID userId, String action, long version) {
        return roomMemberEvent(roomId, userId, action, version, version);
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
