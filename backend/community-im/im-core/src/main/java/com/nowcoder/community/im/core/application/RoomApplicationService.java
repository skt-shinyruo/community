package com.nowcoder.community.im.core.application;

import com.nowcoder.community.common.tx.AfterCommitExecutor;
import com.nowcoder.community.im.common.projection.RoomMembershipSnapshot;
import com.nowcoder.community.im.core.application.result.RoomResults;
import com.nowcoder.community.im.core.domain.event.RoomMemberChangePublisher;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.domain.service.RoomMembershipDomainService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomApplicationService {

    private final RoomMembershipDomainService membershipService;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReadStateRepository readStateRepository;
    private final RoomMemberChangePublisher changePublisher;

    public RoomApplicationService(
            RoomMembershipDomainService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository,
            RoomMemberChangePublisher changePublisher
    ) {
        this.membershipService = membershipService;
        this.roomMessageRepository = roomMessageRepository;
        this.readStateRepository = readStateRepository;
        this.changePublisher = changePublisher;
    }

    @Transactional
    public RoomResults.Created createRoom(UUID creatorUserId, String name) {
        UUID roomId = membershipService.createRoom(creatorUserId, name);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishJoined(roomId, creatorUserId));
        return new RoomResults.Created(roomId);
    }

    @Transactional
    public void joinRoom(UUID userId, UUID roomId) {
        boolean joined = membershipService.joinRoom(userId, roomId);
        if (joined) {
            AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishJoined(roomId, userId));
        }
    }

    @Transactional
    public void leaveRoom(UUID userId, UUID roomId) {
        membershipService.leaveRoom(userId, roomId);
        AfterCommitExecutor.runAfterCommit(() -> changePublisher.publishLeft(roomId, userId));
    }

    @Transactional(readOnly = true)
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

    @Transactional
    public void markRead(UUID viewerId, UUID roomId, long requestedLastReadSeq) {
        assertRoomMember(roomId, viewerId);

        long lastReadSeq = Math.max(0L, requestedLastReadSeq);
        if (lastReadSeq > 0) {
            readStateRepository.updateLastReadSeqMax(roomId, viewerId, lastReadSeq);
        }
    }

    @Transactional(readOnly = true)
    public RoomMembershipSnapshot membershipSnapshot(UUID afterRoomId, UUID afterUserId, int limit) {
        return membershipService.snapshot(afterRoomId, afterUserId, limit);
    }

    private void assertRoomMember(UUID roomId, UUID userId) {
        if (!membershipService.isMember(roomId, userId)) {
            throw new AccessDeniedException("not a room member");
        }
    }
}
