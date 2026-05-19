package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.ConversationApplicationService;
import com.nowcoder.community.im.core.application.result.ConversationResults;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/im/conversations")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;

    public ConversationController(ConversationApplicationService conversationApplicationService) {
        this.conversationApplicationService = conversationApplicationService;
    }

    @GetMapping
    public Result<List<ConversationListItem>> listConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        return Result.ok(conversationApplicationService.listConversations(me, page, size).stream()
                .map(ConversationController::toListItem)
                .toList());
    }

    @GetMapping("/{conversationId}/messages")
    public Result<ConversationMessagesResponse> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        return Result.ok(toMessagesResponse(conversationApplicationService.listMessages(me, conversationId, afterSeq, limit)));
    }

    @PostMapping("/{conversationId}/read")
    public Result<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestBody MarkReadRequest req
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        long lastReadSeq = req == null ? 0L : req.lastReadSeq();
        conversationApplicationService.markRead(me, conversationId, lastReadSeq);
        return Result.ok();
    }

    private static ConversationListItem toListItem(ConversationResults.ListItem item) {
        return new ConversationListItem(
                item.conversationId(),
                item.otherUserId(),
                item.lastSeq(),
                item.lastReadSeq(),
                item.unreadCount(),
                toLastMessage(item.lastMessage())
        );
    }

    private static LastMessage toLastMessage(ConversationResults.LastMessage lastMessage) {
        if (lastMessage == null) {
            return null;
        }
        return new LastMessage(
                lastMessage.messageId(),
                lastMessage.fromUserId(),
                lastMessage.toUserId(),
                lastMessage.content(),
                lastMessage.createdAtEpochMs()
        );
    }

    private static ConversationMessagesResponse toMessagesResponse(ConversationResults.Messages messages) {
        return new ConversationMessagesResponse(
                messages.conversationId(),
                messages.items().stream()
                        .map(ConversationController::toMessageItem)
                        .toList(),
                messages.nextAfterSeq(),
                messages.lastReadSeq()
        );
    }

    private static ConversationMessageItem toMessageItem(ConversationResults.MessageItem item) {
        return new ConversationMessageItem(
                item.conversationId(),
                item.seq(),
                item.messageId(),
                item.fromUserId(),
                item.toUserId(),
                item.content(),
                item.clientMsgId(),
                item.createdAtEpochMs()
        );
    }

    public record MarkReadRequest(long lastReadSeq) {
    }

    public record ConversationMessagesResponse(
            String conversationId,
            List<ConversationMessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record ConversationListItem(
            String conversationId,
            UUID otherUserId,
            long lastSeq,
            long lastReadSeq,
            long unreadCount,
            LastMessage lastMessage
    ) {
    }

    public record LastMessage(
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            long createdAtEpochMs
    ) {
    }

    public record ConversationMessageItem(
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
