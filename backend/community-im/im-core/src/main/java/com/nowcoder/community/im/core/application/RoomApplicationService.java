package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.core.application.result.RoomResults;
import com.nowcoder.community.im.core.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.service.RoomMembershipService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoomApplicationService {

    private final RoomMembershipService membershipService;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReadStateRepository readStateRepository;

    public RoomApplicationService(
            RoomMembershipService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository
    ) {
        this.membershipService = membershipService;
        this.roomMessageRepository = roomMessageRepository;
        this.readStateRepository = readStateRepository;
    }

    public RoomResults.Created createRoom(UUID creatorUserId, String name) {
        return new RoomResults.Created(membershipService.createRoom(creatorUserId, name));
    }

    public void joinRoom(UUID userId, UUID roomId) {
        membershipService.joinRoom(userId, roomId);
    }

    public void leaveRoom(UUID userId, UUID roomId) {
        membershipService.leaveRoom(userId, roomId);
    }

    public RoomResults.Messages listMessages(UUID viewerId, UUID roomId, long afterSeq, int limit) {
        assertRoomMember(roomId, viewerId);

        int l = Math.min(Math.max(1, limit), 200);
        long after = Math.max(0L, afterSeq);
        List<RoomResults.MessageItem> items = roomMessageRepository.listAfterSeq(roomId, after, l)
                .stream()
                .map(r -> new RoomResults.MessageItem(
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
        long lastReadSeq = readStateRepository.getLastReadSeq(roomId, viewerId);
        return new RoomResults.Messages(roomId, items, nextAfterSeq, lastReadSeq);
    }

    public void markRead(UUID viewerId, UUID roomId, long requestedLastReadSeq) {
        assertRoomMember(roomId, viewerId);

        long lastReadSeq = Math.max(0L, requestedLastReadSeq);
        if (lastReadSeq > 0) {
            readStateRepository.updateLastReadSeqMax(roomId, viewerId, lastReadSeq);
        }
    }

    public RoomMembershipSnapshot membershipSnapshot(UUID afterRoomId, UUID afterUserId, int limit) {
        return membershipService.snapshot(afterRoomId, afterUserId, limit);
    }

    private void assertRoomMember(UUID roomId, UUID userId) {
        if (!membershipService.isMember(roomId, userId)) {
            throw new AccessDeniedException("not a room member");
        }
    }
}
