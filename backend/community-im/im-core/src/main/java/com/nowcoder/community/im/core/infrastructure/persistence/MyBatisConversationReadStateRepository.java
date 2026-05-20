package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.ConversationReadStateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisConversationReadStateRepository implements ConversationReadStateRepository {

    private final ConversationReadStateMapper mapper;

    public MyBatisConversationReadStateRepository(ConversationReadStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long getLastReadSeq(String conversationId, UUID userId) {
        Long v = mapper.selectLastReadSeq(conversationId, userId);
        return v == null ? 0L : v;
    }

    @Override
    public void updateLastReadSeqMax(String conversationId, UUID userId, long lastReadSeq) {
        int updated = mapper.updateLastReadSeqMax(conversationId, userId, lastReadSeq);
        if (updated > 0) {
            return;
        }
        try {
            mapper.insert(conversationId, userId, lastReadSeq);
        } catch (DuplicateKeyException ignore) {
            mapper.updateLastReadSeqMax(conversationId, userId, lastReadSeq);
        }
    }
}
