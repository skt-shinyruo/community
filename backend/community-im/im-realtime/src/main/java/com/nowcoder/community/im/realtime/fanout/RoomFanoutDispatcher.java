package com.nowcoder.community.im.realtime.fanout;

public interface RoomFanoutDispatcher {

    void dispatch(RoomFanoutCommand command);
}
