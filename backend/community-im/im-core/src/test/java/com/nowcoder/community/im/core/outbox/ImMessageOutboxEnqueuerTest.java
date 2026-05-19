package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImMessageOutboxEnqueuerTest {

    private final JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
    private final ImMessageOutboxEnqueuer enqueuer = new ImMessageOutboxEnqueuer(
            store,
            new ObjectMapper(),
            "im.event.private-persisted",
            "im.event.room-persisted",
            "im.event.private-committed",
            "im.event.room-committed",
            "im.event.private-rejected",
            "im.event.room-rejected",
            "im.event.room-member-changed"
    );

    @Test
    void enqueuePrivateRejectedUsesAttemptIdentity() {
        UUID fromUserId = uuid(1);
        PrivateMessageRejectedEvent event = new PrivateMessageRejectedEvent(
                "legacy-event-id-ignored",
                "req-reject-private",
                "c-reject-private",
                fromUserId,
                uuid(2),
                "conv-1",
                403,
                "policy_denied",
                "denied",
                123L
        );

        enqueuer.enqueuePrivateRejected(event);

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(eventId.capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture());
        assertThat(eventId.getValue()).isEqualTo("im:psr:" + digestAttempt("req-reject-private", "c-reject-private", fromUserId));
    }

    @Test
    void enqueueRoomRejectedUsesAttemptIdentity() {
        UUID fromUserId = uuid(11);
        RoomMessageRejectedEvent event = new RoomMessageRejectedEvent(
                "legacy-event-id-ignored",
                "req-reject-room",
                "c-reject-room",
                fromUserId,
                uuid(12),
                403,
                "not_room_member",
                "not a room member",
                456L
        );

        enqueuer.enqueueRoomRejected(event);

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(eventId.capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture(), org.mockito.ArgumentCaptor.forClass(String.class).capture());
        assertThat(eventId.getValue()).isEqualTo("im:rsr:" + digestAttempt("req-reject-room", "c-reject-room", fromUserId));
    }

    private static String digestAttempt(String requestId, String clientMsgId, UUID fromUserId) {
        try {
            String source = normalize(fromUserId) + "|" + normalize(requestId) + "|" + normalize(clientMsgId);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
