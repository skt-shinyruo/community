package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMessageRepository {

    Optional<RoomMessageRecord> findByIdempotency(UUID roomId, UUID fromUserId, String clientMsgId);

    void insert(RoomMessageRecord message);

    List<RoomMessageRecord> listAfterSeq(UUID roomId, long afterSeqExclusive, int limit);
}
