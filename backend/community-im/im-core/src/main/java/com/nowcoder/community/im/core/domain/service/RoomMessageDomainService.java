package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;
import com.nowcoder.community.im.core.domain.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.domain.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import com.nowcoder.community.im.core.support.IdGenerator;

import java.time.Instant;
import java.util.UUID;

public class RoomMessageDomainService {

    private final RoomRepository roomRepository;
    private final RoomMembershipDomainService membershipService;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReadStateRepository readStateRepository;
    private final SeqAllocator seqAllocator;
    private final IdGenerator idGenerator;
    private final int maxContentChars;

    public RoomMessageDomainService(
            RoomRepository roomRepository,
            RoomMembershipDomainService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            int maxContentChars
    ) {
        this.roomRepository = roomRepository;
        this.membershipService = membershipService;
        this.roomMessageRepository = roomMessageRepository;
        this.readStateRepository = readStateRepository;
        this.seqAllocator = seqAllocator;
        this.idGenerator = idGenerator;
        this.maxContentChars = Math.max(1, maxContentChars);
    }

    public RoomMessageRecord persist(
            UUID roomId,
            UUID fromUserId,
            String content,
            String clientMsgId
    ) {
        if (clientMsgId == null || clientMsgId.isBlank()) {
            throw new IllegalArgumentException("clientMsgId required");
        }
        String normalizedContent = content == null ? "" : content;
        if (normalizedContent.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (normalizedContent.length() > maxContentChars) {
            throw new IllegalArgumentException("content too long (max=" + maxContentChars + ")");
        }

        if (!roomRepository.exists(roomId)) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        if (!membershipService.isMember(roomId, fromUserId)) {
            throw new SecurityException("not a room member");
        }

        var existing = roomMessageRepository.findByIdempotency(roomId, fromUserId, clientMsgId);
        if (existing.isPresent()) {
            return existing.get();
        }

        long seq = seqAllocator.nextRoomSeq(roomId);
        UUID messageId = idGenerator.nextId();
        Instant now = Instant.now();

        RoomMessageRecord message = new RoomMessageRecord(
                roomId,
                seq,
                messageId,
                fromUserId,
                normalizedContent,
                clientMsgId,
                now
        );
        roomMessageRepository.insert(message);

        // Sender has read their own outgoing message.
        readStateRepository.updateLastReadSeqMax(roomId, fromUserId, seq);

        return message;
    }
}
