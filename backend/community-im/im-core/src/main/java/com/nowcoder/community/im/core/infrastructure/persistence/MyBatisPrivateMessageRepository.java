package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.PrivateMessageDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.PrivateMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisPrivateMessageRepository implements PrivateMessageRepository {

    private final PrivateMessageMapper mapper;

    public MyBatisPrivateMessageRepository(PrivateMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<PrivateMessageRecord> findByIdempotency(String conversationId, UUID fromUserId, String clientMsgId) {
        List<PrivateMessageDataObject> rows = mapper.selectByIdempotency(conversationId, fromUserId, clientMsgId);
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).toDomain());
    }

    @Override
    public void insert(PrivateMessageRecord row) {
        mapper.insert(PrivateMessageDataObject.fromDomain(row));
    }

    @Override
    public List<PrivateMessageRecord> listAfterSeq(String conversationId, long afterSeqExclusive, int limit) {
        return mapper.selectAfterSeq(conversationId, afterSeqExclusive, limit).stream()
                .map(PrivateMessageDataObject::toDomain)
                .toList();
    }
}
