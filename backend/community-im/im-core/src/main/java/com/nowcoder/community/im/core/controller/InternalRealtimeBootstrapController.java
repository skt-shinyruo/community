package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/im/realtime")
public class InternalRealtimeBootstrapController {

    private final RoomMembershipService roomMembershipService;

    public InternalRealtimeBootstrapController(RoomMembershipService roomMembershipService) {
        this.roomMembershipService = roomMembershipService;
    }

    @GetMapping("/users/{userId}/rooms")
    public RoomIdPage listRoomsByUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable int userId,
            @RequestParam(name = "cursor", required = false, defaultValue = "0") long cursorExclusive,
            @RequestParam(name = "limit", required = false, defaultValue = "1000") int limit
    ) {
        int callerId = CurrentUser.userIdOrThrow(jwt);
        if (callerId != userId) {
            throw new AccessDeniedException("userId mismatch");
        }
        long cursor = Math.max(0L, cursorExclusive);
        int l = Math.min(Math.max(1, limit), 5000);
        List<Long> roomIds = roomMembershipService.listRoomIdsByUser(userId, cursor, l);
        boolean hasMore = roomIds.size() >= l;
        long nextCursorExclusive = roomIds.isEmpty() ? cursor : roomIds.get(roomIds.size() - 1);
        return new RoomIdPage(roomIds, nextCursorExclusive, hasMore);
    }

    public record RoomIdPage(List<Long> roomIds, long nextCursorExclusive, boolean hasMore) {
    }
}

