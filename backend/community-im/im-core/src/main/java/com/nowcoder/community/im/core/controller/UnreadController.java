package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.service.UnreadService;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/im/unread")
public class UnreadController {

    private final UnreadService unreadService;

    public UnreadController(UnreadService unreadService) {
        this.unreadService = unreadService;
    }

    @GetMapping("/summary")
    public Result<UnreadSummaryResponse> summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "limit", required = false, defaultValue = "500") int limit
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
        int l = Math.min(Math.max(1, limit), 5000);
        List<UnreadService.RoomUnreadItem> rooms = unreadService.listRoomUnread(me, l);
        List<UnreadService.ConversationUnreadItem> conversations = unreadService.listConversationUnread(me, l);
        return Result.ok(new UnreadSummaryResponse(rooms, conversations));
    }

    public record UnreadSummaryResponse(
            List<UnreadService.RoomUnreadItem> rooms,
            List<UnreadService.ConversationUnreadItem> conversations
    ) {
    }
}
