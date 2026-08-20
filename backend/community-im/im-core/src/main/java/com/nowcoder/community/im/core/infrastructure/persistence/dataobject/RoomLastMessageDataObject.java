package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import java.time.Instant;
import java.util.UUID;

public record RoomLastMessageDataObject(UUID messageId, UUID fromUserId, String content, Instant createdAt) {
}
