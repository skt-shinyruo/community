package com.nowcoder.community.im.core.domain.repository;

import java.util.UUID;

public interface ConversationRepository {

    boolean exists(String conversationId);

    void ensureExists(String conversationId, UUID userA, UUID userB);

    long selectLastSeqForUpdate(String conversationId);

    void updateLastSeq(String conversationId, long lastSeq);
}
