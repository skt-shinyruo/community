package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.RoomUnreadItem;

import java.util.UUID;

public record RoomUnreadDataObject(UUID roomId, long lastSeq, long lastReadSeq, long unreadCount) {

    public RoomUnreadItem toDomain() {
        return new RoomUnreadItem(roomId, lastSeq, lastReadSeq, unreadCount);
    }
}
