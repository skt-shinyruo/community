package com.nowcoder.community.im.core.service;

public interface RoomMemberChangePublisher {

    void publishJoined(long roomId, int userId);

    void publishLeft(long roomId, int userId);
}

