package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.domain.model.ConversationListItem;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository {

    boolean exists(String conversationId);

    void ensureExists(String conversationId, UUID userA, UUID userB);

    long selectLastSeqForUpdate(String conversationId);

    void updateLastSeq(String conversationId, long lastSeq);

    List<ConversationListItem> listByUser(UUID userId, int limit, long offset);
}
