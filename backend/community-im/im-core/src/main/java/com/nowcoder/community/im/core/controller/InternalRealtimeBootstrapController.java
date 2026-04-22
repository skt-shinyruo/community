package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
            @PathVariable UUID userId,
            @RequestParam(name = "cursor", required = false) UUID cursorExclusive,
            @RequestParam(name = "limit", required = false, defaultValue = "1000") int limit
    ) {
        UUID callerId = CurrentUser.userIdOrThrow(jwt);
        if (!callerId.equals(userId)) {
            throw new AccessDeniedException("userId mismatch");
        }
        int l = Math.min(Math.max(1, limit), 5000);
        List<UUID> roomIds = roomMembershipService.listRoomIdsByUser(userId, cursorExclusive, l);
        boolean hasMore = roomIds.size() >= l;
        UUID nextCursorExclusive = roomIds.isEmpty() ? null : roomIds.get(roomIds.size() - 1);
        return new RoomIdPage(roomIds, nextCursorExclusive, hasMore);
    }

    public record RoomIdPage(List<UUID> roomIds, UUID nextCursorExclusive, boolean hasMore) {
    }
}
