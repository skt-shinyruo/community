package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.common.event.RoomMemberChanged;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaRoomMemberChangePublisherTest {

    @Test
    void publishJoinedEnqueuesRoomMemberChangedOutboxEvent() {
        ImMessageOutboxEnqueuer outboxEnqueuer = mock(ImMessageOutboxEnqueuer.class);
        KafkaRoomMemberChangePublisher publisher = new KafkaRoomMemberChangePublisher(outboxEnqueuer);
        UUID roomId = uuid(1);
        UUID userId = uuid(2);

        publisher.publishJoined(roomId, userId);

        ArgumentCaptor<RoomMemberChanged> eventCaptor = ArgumentCaptor.forClass(RoomMemberChanged.class);
        verify(outboxEnqueuer).enqueueRoomMemberChanged(eventCaptor.capture());
        RoomMemberChanged event = eventCaptor.getValue();
        assertThat(event.eventId()).startsWith("evt_room_member_");
        assertThat(event.eventId().length()).isLessThanOrEqualTo(64);
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.action()).isEqualTo("JOINED");
        assertThat(event.occurredAtEpochMillis()).isPositive();
        assertThat(event.version()).isNotNull().isPositive();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
