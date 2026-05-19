package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.core.service.RoomMessageService;
import org.springframework.stereotype.Service;

@Service
public class RoomMessageApplicationService {

    private final RoomMessageService roomMessageService;

    public RoomMessageApplicationService(RoomMessageService roomMessageService) {
        this.roomMessageService = roomMessageService;
    }

    public RoomMessagePersistedEvent persist(SendRoomTextCommand command) {
        return roomMessageService.persist(command);
    }
}
