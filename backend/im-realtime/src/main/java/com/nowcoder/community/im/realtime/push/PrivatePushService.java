package com.nowcoder.community.im.realtime.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.contracts.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import com.nowcoder.community.im.realtime.presence.WsConnection;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class PrivatePushService {

    private final ConnectionRegistry connectionRegistry;
    private final ObjectMapper objectMapper;

    public PrivatePushService(ConnectionRegistry connectionRegistry, ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
    }

    public void pushPrivateMessage(PrivateMessagePersistedEventV1 event) {
        if (event == null) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(new PrivateMessage(event));
        } catch (JsonProcessingException e) {
            return;
        }

        pushToUser(event.toUserId(), json);
        pushToUser(event.fromUserId(), json);
    }

    private void pushToUser(int userId, String json) {
        Collection<WsConnection> conns = connectionRegistry.listByUserId(userId);
        for (WsConnection conn : conns) {
            conn.trySendText(json);
        }
    }

    public record PrivateMessage(
            String type,
            String conversationId,
            long seq,
            long messageId,
            int fromUserId,
            int toUserId,
            String content,
            long createdAtEpochMs
    ) {
        public PrivateMessage(PrivateMessagePersistedEventV1 e) {
            this(
                    "privateMessage",
                    e.conversationId(),
                    e.seq(),
                    e.messageId(),
                    e.fromUserId(),
                    e.toUserId(),
                    e.content(),
                    e.createdAtEpochMs()
            );
        }
    }
}

