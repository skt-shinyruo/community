package com.nowcoder.community.im.core.domain.repository;

import java.util.UUID;

public interface RoomReadStateRepository {

    long getLastReadSeq(UUID roomId, UUID userId);

    void updateLastReadSeqMax(UUID roomId, UUID userId, long lastReadSeq);
}
