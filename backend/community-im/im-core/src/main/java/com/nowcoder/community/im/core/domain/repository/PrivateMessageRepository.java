package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivateMessageRepository {

    Optional<PrivateMessageRecord> findByIdempotency(String conversationId, UUID fromUserId, String clientMsgId);

    void insert(PrivateMessageRecord message);

    List<PrivateMessageRecord> listAfterSeq(String conversationId, long afterSeqExclusive, int limit);
}
