package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.ConversationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisConversationRepository implements ConversationRepository {

    private final ConversationMapper mapper;

    public MyBatisConversationRepository(ConversationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(String conversationId) {
        return mapper.countByConversationId(conversationId) > 0;
    }

    @Override
    public void ensureExists(String conversationId, UUID userA, UUID userB) {
        try {
            mapper.insertConversation(conversationId, userA, userB);
        } catch (DuplicateKeyException ignore) {
            // idempotent: already exists
        }
    }

    @Override
    public long selectLastSeqForUpdate(String conversationId) {
        Long v = mapper.selectLastSeqForUpdate(conversationId);
        if (v == null) {
            throw new IllegalArgumentException("conversation not found: " + conversationId);
        }
        return v;
    }

    @Override
    public void updateLastSeq(String conversationId, long lastSeq) {
        mapper.updateLastSeq(conversationId, lastSeq);
    }
}
