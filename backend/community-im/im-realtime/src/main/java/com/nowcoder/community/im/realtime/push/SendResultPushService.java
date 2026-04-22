package com.nowcoder.community.im.realtime.push;

import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.PrivateMessageRejectedEventV1;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEventV1;
import com.nowcoder.community.im.common.event.RoomMessageRejectedEventV1;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.ws.WsProtocol;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class SendResultPushService {

    private final ConnectionRegistry connectionRegistry;

    public SendResultPushService(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void pushPrivateCommitted(PrivateMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                WsProtocol.sendCommitted(
                        "sendPrivateText",
                        event.clientMsgId(),
                        event.requestId(),
                        extras(
                                "conversationId", event.conversationId(),
                                "messageId", event.messageId(),
                                "seq", event.seq()
                        )
                )
        );
    }

    public void pushRoomCommitted(RoomMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                WsProtocol.sendCommitted(
                        "sendRoomText",
                        event.clientMsgId(),
                        event.requestId(),
                        extras(
                                "roomId", event.roomId(),
                                "messageId", event.messageId(),
                                "seq", event.seq()
                        )
                )
        );
    }

    public void pushPrivateRejected(PrivateMessageRejectedEventV1 event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                WsProtocol.sendRejected(
                        "sendPrivateText",
                        event.clientMsgId(),
                        event.requestId(),
                        event.code(),
                        event.reasonCode(),
                        event.message()
                )
        );
    }

    public void pushRoomRejected(RoomMessageRejectedEventV1 event) {
        if (event == null) {
            return;
        }
        pushToUser(
                event.fromUserId(),
                WsProtocol.sendRejected(
                        "sendRoomText",
                        event.clientMsgId(),
                        event.requestId(),
                        event.code(),
                        event.reasonCode(),
                        event.message()
                )
        );
    }

    private void pushToUser(UUID userId, String json) {
        if (userId == null || json == null) {
            return;
        }
        connectionRegistry.forEachConnectionByUserId(userId, conn -> conn.trySendText(json));
    }

    private Map<String, Object> extras(Object... keyValues) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            payload.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return payload;
    }
}
