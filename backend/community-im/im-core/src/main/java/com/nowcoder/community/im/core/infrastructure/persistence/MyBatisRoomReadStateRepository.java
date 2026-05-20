package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.RoomReadStateMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisRoomReadStateRepository implements RoomReadStateRepository {

    private final RoomReadStateMapper mapper;

    public MyBatisRoomReadStateRepository(RoomReadStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long getLastReadSeq(UUID roomId, UUID userId) {
        Long v = mapper.selectLastReadSeq(roomId, userId);
        return v == null ? 0L : v;
    }

    @Override
    public void updateLastReadSeqMax(UUID roomId, UUID userId, long lastReadSeq) {
        int updated = mapper.updateLastReadSeqMax(roomId, userId, lastReadSeq);
        if (updated > 0) {
            return;
        }
        try {
            mapper.insert(roomId, userId, lastReadSeq);
        } catch (DuplicateKeyException ignore) {
            mapper.updateLastReadSeqMax(roomId, userId, lastReadSeq);
        }
    }
}
