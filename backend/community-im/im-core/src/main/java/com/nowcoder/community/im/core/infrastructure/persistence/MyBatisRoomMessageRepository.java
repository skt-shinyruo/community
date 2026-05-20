package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.dataobject.RoomMessageDataObject;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.RoomMessageMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MyBatisRoomMessageRepository implements RoomMessageRepository {

    private final RoomMessageMapper mapper;

    public MyBatisRoomMessageRepository(RoomMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RoomMessageRecord> findByIdempotency(UUID roomId, UUID fromUserId, String clientMsgId) {
        List<RoomMessageDataObject> rows = mapper.selectByIdempotency(roomId, fromUserId, clientMsgId);
        return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0).toDomain());
    }

    @Override
    public void insert(RoomMessageRecord row) {
        mapper.insert(RoomMessageDataObject.fromDomain(row));
    }

    @Override
    public List<RoomMessageRecord> listAfterSeq(UUID roomId, long afterSeqExclusive, int limit) {
        return mapper.selectAfterSeq(roomId, afterSeqExclusive, limit).stream()
                .map(RoomMessageDataObject::toDomain)
                .toList();
    }
}
