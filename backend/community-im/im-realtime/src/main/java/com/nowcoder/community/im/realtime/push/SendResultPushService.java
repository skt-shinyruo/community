package com.nowcoder.community.im.realtime.push;

import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEvent;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEvent;
import com.nowcoder.community.im.common.ws.CommittedFrame;
import com.nowcoder.community.im.common.ws.RejectFrame;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.ws.ImFrameCodec;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SendResultPushService {

    private final ConnectionRegistry connectionRegistry;
    private final ImFrameCodec frameCodec;

    public SendResultPushService(ConnectionRegistry connectionRegistry, ImFrameCodec frameCodec) {
        this.connectionRegistry = connectionRegistry;
        this.frameCodec = frameCodec;
    }

    public void pushPrivateCommitted(PrivateMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                frameCodec.write(new CommittedFrame(
                        "committed",
                        "sendPrivateText",
                        event.clientMsgId(),
                        event.requestId(),
                        event.conversationId(),
                        null,
                        event.messageId(),
                        event.seq()
                ))
        );
    }

    public void pushRoomCommitted(RoomMessagePersistedEvent event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                frameCodec.write(new CommittedFrame(
                        "committed",
                        "sendRoomText",
                        event.clientMsgId(),
                        event.requestId(),
                        null,
                        event.roomId(),
                        event.messageId(),
                        event.seq()
                ))
        );
    }

    public void pushPrivateRejected(PrivateMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                frameCodec.write(new RejectFrame(
                        "reject",
                        "sendPrivateText",
                        event.clientMsgId(),
                        event.requestId(),
                        event.code(),
                        event.reasonCode(),
                        event.message()
                ))
        );
    }

    public void pushRoomRejected(RoomMessageRejectedEvent event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                frameCodec.write(new RejectFrame(
                        "reject",
                        "sendRoomText",
                        event.clientMsgId(),
                        event.requestId(),
                        event.code(),
                        event.reasonCode(),
                        event.message()
                ))
        );
    }

    private void pushToUser(UUID userId, String json) {
        if (userId == null || json == null) {
            return;
        }
        connectionRegistry.forEachConnectionByUserId(userId, conn -> conn.trySendText(json));
    }
}
