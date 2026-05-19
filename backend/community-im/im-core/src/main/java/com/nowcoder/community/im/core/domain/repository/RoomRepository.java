package com.nowcoder.community.im.core.domain.repository;

import java.util.UUID;

public interface RoomRepository {

    boolean exists(UUID roomId);

    void insertRoom(UUID roomId, String name);

    long selectLastSeqForUpdate(UUID roomId);

    void updateLastSeq(UUID roomId, long lastSeq);
}
