package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.core.repository.RoomMessageRepository;
import com.nowcoder.community.im.core.repository.RoomReadStateRepository;
import com.nowcoder.community.im.core.repository.RoomRepository;
import com.nowcoder.community.im.core.repository.SeqAllocator;
import com.nowcoder.community.im.core.support.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RoomMessageService {

    private final RoomRepository roomRepository;
    private final RoomMembershipService membershipService;
    private final RoomMessageRepository roomMessageRepository;
    private final RoomReadStateRepository readStateRepository;
    private final SeqAllocator seqAllocator;
    private final IdGenerator idGenerator;
    private final int maxContentChars;

    public RoomMessageService(
            RoomRepository roomRepository,
            RoomMembershipService membershipService,
            RoomMessageRepository roomMessageRepository,
            RoomReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            @Value("${im.message.max-chars:10000}") int maxContentChars
    ) {
        this.roomRepository = roomRepository;
        this.membershipService = membershipService;
        this.roomMessageRepository = roomMessageRepository;
        this.readStateRepository = readStateRepository;
        this.seqAllocator = seqAllocator;
        this.idGenerator = idGenerator;
        this.maxContentChars = Math.max(1, maxContentChars);
    }

    @Transactional
    public RoomMessagePersistedEvent persist(SendRoomTextCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("command required");
        }
        if (cmd.clientMsgId() == null || cmd.clientMsgId().isBlank()) {
            throw new IllegalArgumentException("clientMsgId required");
        }
        String content = cmd.content() == null ? "" : cmd.content();
        if (content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (content.length() > maxContentChars) {
            throw new IllegalArgumentException("content too long (max=" + maxContentChars + ")");
        }

        UUID roomId = cmd.roomId();
        UUID fromUserId = cmd.fromUserId();

        if (!roomRepository.exists(roomId)) {
            throw new IllegalArgumentException("room not found: " + roomId);
        }
        if (!membershipService.isMember(roomId, fromUserId)) {
            throw new SecurityException("not a room member");
        }

        var existing = roomMessageRepository.findByIdempotency(roomId, fromUserId, cmd.clientMsgId());
        if (existing.isPresent()) {
            var m = existing.get();
            return new RoomMessagePersistedEvent(
                    "evt_" + m.messageId(),
                    m.roomId(),
                    m.seq(),
                    m.messageId(),
                    m.fromUserId(),
                    cmd.requestId(),
                    cmd.clientMsgId(),
                    m.createdAt().toEpochMilli()
            );
        }

        long seq = seqAllocator.nextRoomSeq(roomId);
        UUID messageId = idGenerator.nextId();
        Instant now = Instant.now();

        roomMessageRepository.insert(new RoomMessageRepository.RoomMessageRow(
                roomId,
                seq,
                messageId,
                fromUserId,
                content,
                cmd.clientMsgId(),
                now
        ));

        // Sender has read their own outgoing message.
        readStateRepository.updateLastReadSeqMax(roomId, fromUserId, seq);

        return new RoomMessagePersistedEvent(
                "evt_" + messageId,
                roomId,
                seq,
                messageId,
                fromUserId,
                cmd.requestId(),
                cmd.clientMsgId(),
                now.toEpochMilli()
        );
    }
}
