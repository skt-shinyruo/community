package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.ConversationApplicationService;
import com.nowcoder.community.im.core.application.result.ConversationResults;
import com.nowcoder.community.common.security.jwt.JwtSubjects;
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
    public Result<List<ConversationResults.ListItem>> listConversations(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        return Result.ok(conversationApplicationService.listConversations(me, page, size));
    }

    @GetMapping("/page")
    public Result<ConversationResults.Page> listConversationPage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "cursor", required = false, defaultValue = "") String cursor,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        return Result.ok(conversationApplicationService.listConversationPage(me, cursor, size));
    }

    @GetMapping("/{conversationId}/messages/history")
    public Result<ConversationResults.History> listMessageHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestParam(name = "beforeSeq", required = false) Long beforeSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        return Result.ok(conversationApplicationService.listMessageHistory(me, conversationId, beforeSeq, limit));
    }

    @GetMapping("/{conversationId}/messages")
    public Result<ConversationResults.Messages> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        return Result.ok(conversationApplicationService.listMessages(me, conversationId, afterSeq, limit));
    }

    @PostMapping("/{conversationId}/read")
    public Result<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String conversationId,
            @RequestBody MarkReadRequest req
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        long lastReadSeq = req == null ? 0L : req.lastReadSeq();
        conversationApplicationService.markRead(me, conversationId, lastReadSeq);
        return Result.ok();
    }

    public record MarkReadRequest(long lastReadSeq) {
    }
}
