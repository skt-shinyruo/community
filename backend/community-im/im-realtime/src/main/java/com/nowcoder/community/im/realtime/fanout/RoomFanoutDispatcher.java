package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;

public interface RoomFanoutDispatcher {

    void dispatch(RoomFanoutCommand command);
}
