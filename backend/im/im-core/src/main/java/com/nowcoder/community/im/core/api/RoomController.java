package com.nowcoder.community.im.core.api;

import com.nowcoder.community.im.core.db.RoomMessageRepository;
import com.nowcoder.community.im.core.db.RoomReadStateRepository;
import com.nowcoder.community.im.core.security.CurrentUser;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public CreateRoomResponse createRoom(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRoomRequest req) {
        int me = CurrentUser.userIdOrThrow(jwt);
        String name = req == null ? null : req.name();
        long roomId = membershipService.createRoom(me, name);
        return new CreateRoomResponse(roomId);
    }

    @PostMapping("/{roomId}/join")
    public void joinRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable long roomId) {
        int me = CurrentUser.userIdOrThrow(jwt);
        membershipService.joinRoom(me, roomId);
    }

    @PostMapping("/{roomId}/leave")
    public void leaveRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable long roomId) {
        int me = CurrentUser.userIdOrThrow(jwt);
        membershipService.leaveRoom(me, roomId);
    }

    @GetMapping("/{roomId}/messages")
    public RoomMessagesResponse listMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long roomId,
            @RequestParam(name = "afterSeq", required = false, defaultValue = "0") long afterSeq,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
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
        return new RoomMessagesResponse(roomId, items, nextAfterSeq, lastReadSeq);
    }

    @PostMapping("/{roomId}/read")
    public void markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long roomId,
            @RequestBody MarkReadRequest req
    ) {
        int me = CurrentUser.userIdOrThrow(jwt);
        if (!membershipService.isMember(roomId, me)) {
            throw new AccessDeniedException("not a room member");
        }
        long lastReadSeq = req == null ? 0L : Math.max(0L, req.lastReadSeq());
        readStateRepository.updateLastReadSeqMax(roomId, me, lastReadSeq);
    }

    public record CreateRoomRequest(String name) {
    }

    public record CreateRoomResponse(long roomId) {
    }

    public record MarkReadRequest(long lastReadSeq) {
    }

    public record RoomMessagesResponse(
            long roomId,
            List<RoomMessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record RoomMessageItem(
            long roomId,
            long seq,
            long messageId,
            int fromUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}

