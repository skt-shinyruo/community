package com.nowcoder.community.im.core.infrastructure.persistence;

import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import com.nowcoder.community.im.core.infrastructure.persistence.mapper.RoomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class MyBatisRoomRepository implements RoomRepository {

    private final RoomMapper mapper;

    public MyBatisRoomRepository(RoomMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean exists(UUID roomId) {
        return mapper.countByRoomId(roomId) > 0;
    }

    @Override
    public void insertRoom(UUID roomId, String name) {
        try {
            mapper.insertRoom(roomId, name);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("room already exists: " + roomId);
        }
    }

    @Override
    public long selectLastSeqForUpdate(UUID roomId) {
        Long v = mapper.selectLastSeqForUpdate(roomId);
        if (v == null) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        return v;
    }

    @Override
    public void updateLastSeq(UUID roomId, long lastSeq) {
        mapper.updateLastSeq(roomId, lastSeq);
    }
}
