package com.nowcoder.community.im.core.domain.model;

import java.util.UUID;

public record RoomUnreadItem(UUID roomId, long lastSeq, long lastReadSeq, long unreadCount) {
}
