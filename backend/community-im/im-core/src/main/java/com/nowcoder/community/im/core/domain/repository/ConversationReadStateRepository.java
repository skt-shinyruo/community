package com.nowcoder.community.im.core.domain.repository;

import java.util.UUID;

public interface ConversationReadStateRepository {

    long getLastReadSeq(String conversationId, UUID userId);

    void updateLastReadSeqMax(String conversationId, UUID userId, long lastReadSeq);
}
