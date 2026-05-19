package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.realtime.client.ImServiceClientProperties;
import com.nowcoder.community.im.realtime.push.RoomFanoutCoalescer;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class HttpRoomFanoutDispatcherTest {

    @Test
    void localDuplicateTargetCommandIsTreatedAsAlreadyDelivered() {
        RoomFanoutTargetService targetService = new RoomFanoutTargetService(
                mock(RoomFanoutCoalescer.class),
                sessionProperties()
        );
        HttpRoomFanoutDispatcher dispatcher = new HttpRoomFanoutDispatcher(
                workerDirectory(),
                targetService,
                new RoomFanoutProperties(),
                jwtProperties(),
                new ImServiceClientProperties(),
                sessionProperties()
        );
        RoomFanoutCommand command = new RoomFanoutCommand(
                "worker-a",
                java.util.UUID.fromString("00000000-0000-7000-8000-000000000001"),
                42L,
                "evt-duplicate",
                1000L
        );

        dispatcher.dispatch(command);

        assertThatCode(() -> dispatcher.dispatch(command)).doesNotThrowAnyException();
    }

    private static RealtimeWorkerDirectory workerDirectory() {
        return new RealtimeWorkerDirectory(List::of, sessionProperties(), new RoomFanoutProperties());
    }

    private static ImSessionProperties sessionProperties() {
        ImSessionProperties properties = new ImSessionProperties();
        properties.setWorkerId("worker-a");
        return properties;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setHmacSecret("room-fanout-dispatcher-test-secret-123456");
        properties.setIssuer("community-auth");
        return properties;
    }
}
