package com.nowcoder.community.im.realtime.push;

import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.ws.PrivateMessageFrame;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PrivatePushService {

    private final ConnectionRegistry connectionRegistry;
    private final ImFrameCodec frameCodec;

    public PrivatePushService(ConnectionRegistry connectionRegistry, ImFrameCodec frameCodec) {
        this.connectionRegistry = connectionRegistry;
        this.frameCodec = frameCodec;
    }

    public void pushPrivateMessage(PrivateMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        String json;
        try {
            json = frameCodec.write(new PrivateMessageFrame(
                    "privateMessage",
                    event.conversationId(),
                    event.seq(),
                    event.messageId(),
                    event.fromUserId(),
                    event.toUserId(),
                    event.content(),
                    event.createdAtEpochMs()
            ));
        } catch (RuntimeException e) {
            return;
        }

        pushToUser(event.toUserId(), json);
        pushToUser(event.fromUserId(), json);
    }

    private void pushToUser(UUID userId, String json) {
        connectionRegistry.forEachConnectionByUserId(userId, conn -> conn.trySendText(json));
    }
}
