package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomFanoutTargetController {

    private final RoomFanoutTargetService roomFanoutTargetService;

    public RoomFanoutTargetController(RoomFanoutTargetService roomFanoutTargetService) {
        this.roomFanoutTargetService = roomFanoutTargetService;
    }

    @PostMapping("/internal/im/realtime/fanout/room")
    public ResponseEntity<Void> fanoutRoom(@RequestBody RoomFanoutCommand command) {
        RoomFanoutTargetResult result = roomFanoutTargetService.apply(command);
        return switch (result) {
            case ACCEPTED, DUPLICATE -> ResponseEntity.accepted().build();
            case WRONG_TARGET -> ResponseEntity.status(409).build();
            case INVALID -> ResponseEntity.badRequest().build();
        };
    }
}
