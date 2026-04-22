package com.nowcoder.community.im.realtime.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.realtime.presence.ConnectionRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

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

    private void pushToUser(UUID userId, String json) {
        connectionRegistry.forEachConnectionByUserId(userId, conn -> conn.trySendText(json));
    }

    public record PrivateMessage(
            String type,
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
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
