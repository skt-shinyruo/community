package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/im/realtime/projections")
public class InternalRealtimeProjectionController {

    private final RoomMembershipService roomMembershipService;

    public InternalRealtimeProjectionController(RoomMembershipService roomMembershipService) {
        this.roomMembershipService = roomMembershipService;
    }

    @GetMapping("/room-memberships")
    public ResponseEntity<RoomMembershipSnapshot> roomMemberships(
            @RequestParam(name = "afterRoomId", required = false) UUID afterRoomId,
            @RequestParam(name = "afterUserId", required = false) UUID afterUserId,
            @RequestParam(name = "limit", defaultValue = "500") int limit
    ) {
        try {
            return ResponseEntity.ok(roomMembershipService.snapshot(afterRoomId, afterUserId, limit));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
