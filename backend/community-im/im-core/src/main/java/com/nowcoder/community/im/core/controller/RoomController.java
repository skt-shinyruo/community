package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/im/rooms")
public class RoomController {

    private final RoomMembershipService membershipService;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReadStateRepository readStateRepository;

    public RoomController(
            RoomMembershipService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository
    ) {
        this.membershipService = membershipService;
        this.roomMessageRepository = roomMessageRepository;
        this.readStateRepository = readStateRepository;
    }

    @PostMapping
    public Result<CreateRoomResponse> createRoom(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRoomRequest req) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        String name = req == null ? null : req.name();
        UUID roomId = membershipService.createRoom(me, name);
        return Result.ok(new CreateRoomResponse(roomId));
    }

    @PostMapping("/{roomId}/join")
    public Result<Void> joinRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        membershipService.joinRoom(me, roomId);
        return Result.ok();
    }

    @PostMapping("/{roomId}/leave")
    public Result<Void> leaveRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID roomId) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        membershipService.leaveRoom(me, roomId);
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
        if (!membershipService.isMember(roomId, me)) {
            throw new AccessDeniedException("not a room member");
        }

        int l = Math.min(Math.max(1, limit), 200);
        long after = Math.max(0L, afterSeq);
        List<RoomMessageRepository.RoomMessageRow> rows = roomMessageRepository.listAfterSeq(roomId, after, l);
        List<RoomMessageItem> items = rows.stream()
                .map(r -> new RoomMessageItem(
                        r.roomId(),
                        r.seq(),
                        r.messageId(),
                        r.fromUserId(),
                        r.content(),
                        r.clientMsgId(),
                        r.createdAt().toEpochMilli()
                ))
                .toList();
        long nextAfterSeq = items.isEmpty() ? after : items.get(items.size() - 1).seq();
        long lastReadSeq = readStateRepository.getLastReadSeq(roomId, me);
        return Result.ok(new RoomMessagesResponse(roomId, items, nextAfterSeq, lastReadSeq));
    }

    @PostMapping("/{roomId}/read")
    public Result<Void> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestBody MarkReadRequest req
    ) {
        UUID me = CurrentUser.userIdOrThrow(jwt);
        if (!membershipService.isMember(roomId, me)) {
            throw new AccessDeniedException("not a room member");
        }
        long lastReadSeq = req == null ? 0L : Math.max(0L, req.lastReadSeq());
        if (lastReadSeq > 0) {
            readStateRepository.updateLastReadSeqMax(roomId, me, lastReadSeq);
        }
        return Result.ok();
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
