package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.UnreadApplicationService;
import com.nowcoder.community.im.core.application.result.UnreadSummaryResult;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/im/unread")
public class UnreadController {

    private final UnreadApplicationService unreadApplicationService;

    public UnreadController(UnreadApplicationService unreadApplicationService) {
        this.unreadApplicationService = unreadApplicationService;
    }

    @GetMapping("/summary")
    public Result<UnreadSummaryResponse> summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "limit", required = false, defaultValue = "500") int limit
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        return Result.ok(toResponse(unreadApplicationService.summary(me, limit)));
    }

    private static UnreadSummaryResponse toResponse(UnreadSummaryResult summary) {
        return new UnreadSummaryResponse(
                summary.rooms().stream()
                        .map(item -> new RoomUnreadItem(
                                item.roomId(),
                                item.lastSeq(),
                                item.lastReadSeq(),
                                item.unreadCount()
                        ))
                        .toList(),
                summary.conversations().stream()
                        .map(item -> new ConversationUnreadItem(
                                item.conversationId(),
                                item.lastSeq(),
                                item.lastReadSeq(),
                                item.unreadCount()
                        ))
                        .toList()
        );
    }

    public record UnreadSummaryResponse(
            List<RoomUnreadItem> rooms,
            List<ConversationUnreadItem> conversations
    ) {
    }

    public record RoomUnreadItem(UUID roomId, long lastSeq, long lastReadSeq, long unreadCount) {
    }

    public record ConversationUnreadItem(String conversationId, long lastSeq, long lastReadSeq, long unreadCount) {
    }
}
