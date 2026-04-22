package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEventV1;
import com.nowcoder.community.im.core.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.repository.ConversationRepository;
import com.nowcoder.community.im.core.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.repository.SeqAllocator;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import com.nowcoder.community.im.core.support.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PrivateMessageService {

    private final ConversationRepository conversationRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final SeqAllocator seqAllocator;
    private final IdGenerator idGenerator;
    private final int maxContentChars;

    public PrivateMessageService(
            ConversationRepository conversationRepository,
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            @Value("${im.message.max-chars:10000}") int maxContentChars
    ) {
        this.conversationRepository = conversationRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.readStateRepository = readStateRepository;
        this.seqAllocator = seqAllocator;
        this.idGenerator = idGenerator;
        this.maxContentChars = Math.max(1, maxContentChars);
    }

    @Transactional
    public PrivateMessagePersistedEventV1 persist(SendPrivateTextCommandV1 cmd) {
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

        UUID fromUserId = cmd.fromUserId();
        UUID toUserId = cmd.toUserId();
        String derivedConversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        if (cmd.conversationId() == null || !derivedConversationId.equals(cmd.conversationId())) {
            throw new IllegalArgumentException("conversationId mismatch");
        }

        UUID userA = fromUserId.compareTo(toUserId) <= 0 ? fromUserId : toUserId;
        UUID userB = userA.equals(fromUserId) ? toUserId : fromUserId;
        conversationRepository.ensureExists(derivedConversationId, userA, userB);

        var existing = privateMessageRepository.findByIdempotency(derivedConversationId, fromUserId, cmd.clientMsgId());
        if (existing.isPresent()) {
            var m = existing.get();
            return new PrivateMessagePersistedEventV1(
                    "evt_" + m.messageId(),
                    m.conversationId(),
                    m.seq(),
                    m.messageId(),
                    m.fromUserId(),
                    m.toUserId(),
                    m.content(),
                    m.createdAt().toEpochMilli()
            );
        }

        long seq = seqAllocator.nextConversationSeq(derivedConversationId);
        UUID messageId = idGenerator.nextId();
        Instant now = Instant.now();

        privateMessageRepository.insert(new PrivateMessageRepository.PrivateMessageRow(
                derivedConversationId,
                seq,
                messageId,
                fromUserId,
                toUserId,
                content,
                cmd.clientMsgId(),
                now
        ));

        // Sender has read their own outgoing message.
        readStateRepository.updateLastReadSeqMax(derivedConversationId, fromUserId, seq);

        return new PrivateMessagePersistedEventV1(
                "evt_" + messageId,
                derivedConversationId,
                seq,
                messageId,
                fromUserId,
                toUserId,
                content,
                now.toEpochMilli()
        );
    }
}
