package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.RoomApplicationService;
import com.nowcoder.community.im.core.application.result.RoomResults;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/im/rooms")
public class RoomController {

    private final RoomApplicationService roomApplicationService;

    public RoomController(RoomApplicationService roomApplicationService) {
        this.roomApplicationService = roomApplicationService;
    }

    @PostMapping
    public Result<CreateRoomResponse> createRoom(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRoomRequest req) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        String name = req == null ? null : req.name();
        RoomResults.Created created = roomApplicationService.createRoom(me, name);
        return Result.ok(new CreateRoomResponse(created.roomId()));
    }

    @PostMapping("/{roomId}/join")
    public Result<Void> joinRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        roomApplicationService.joinRoom(me, roomId);
        return Result.ok();
    }

    @PostMapping("/{roomId}/leave")
    public Result<Void> leaveRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        roomApplicationService.leaveRoom(me, roomId);
        return Result.ok();
    }

    @GetMapping("/{roomId}/messages")
    public Result<RoomMessagesResponse> listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        return Result.ok(toMessagesResponse(roomApplicationService.listMessages(me, roomId, afterSeq, limit)));
    }

    @PostMapping("/{roomId}/read")
    public Result<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestBody MarkReadRequest req
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        long lastReadSeq = req == null ? 0L : req.lastReadSeq();
        roomApplicationService.markRead(me, roomId, lastReadSeq);
        return Result.ok();
    }

    private static RoomMessagesResponse toMessagesResponse(RoomResults.Messages messages) {
        return new RoomMessagesResponse(
                messages.roomId(),
                messages.items().stream()
                        .map(RoomController::toMessageItem)
                        .toList(),
                messages.nextAfterSeq(),
                messages.lastReadSeq()
        );
    }

    private static RoomMessageItem toMessageItem(RoomResults.MessageItem item) {
        return new RoomMessageItem(
                item.roomId(),
                item.seq(),
                item.messageId(),
                item.fromUserId(),
                item.content(),
                item.clientMsgId(),
                item.createdAtEpochMs()
        );
    }

    public record CreateRoomRequest(String name) {
    }

    public record CreateRoomResponse(UUID roomId) {
    }

    public record MarkReadRequest(long lastReadSeq) {
    }

    public record RoomMessagesResponse(
            UUID roomId,
            List<RoomMessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record RoomMessageItem(
            UUID roomId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
