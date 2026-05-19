package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;
import com.nowcoder.community.im.core.domain.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import com.nowcoder.community.im.core.support.IdGenerator;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class PrivateMessageDomainService {

    private final ConversationRepository conversationRepository;
    private final PrivateMessageRepository privateMessageRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final SeqAllocator seqAllocator;
    private final IdGenerator idGenerator;
    private final int maxContentChars;

    public PrivateMessageDomainService(
            ConversationRepository conversationRepository,
            PrivateMessageRepository privateMessageRepository,
            ConversationReadStateRepository readStateRepository,
            SeqAllocator seqAllocator,
            IdGenerator idGenerator,
            int maxContentChars
    ) {
        this.conversationRepository = conversationRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.readStateRepository = readStateRepository;
        this.seqAllocator = seqAllocator;
        this.idGenerator = idGenerator;
        this.maxContentChars = Math.max(1, maxContentChars);
    }

    public PrivateMessageDraft prepare(
            UUID fromUserId,
            UUID toUserId,
            String conversationId,
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

        String derivedConversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);
        if (conversationId == null || !derivedConversationId.equals(conversationId)) {
            throw new IllegalArgumentException("conversationId mismatch");
        }

        return new PrivateMessageDraft(
                fromUserId,
                toUserId,
                derivedConversationId,
                normalizedContent,
                clientMsgId
        );
    }

    public PrivateMessageRecord persist(PrivateMessageDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("message draft required");
        }
        UUID userA = draft.fromUserId().compareTo(draft.toUserId()) <= 0 ? draft.fromUserId() : draft.toUserId();
        UUID userB = userA.equals(draft.fromUserId()) ? draft.toUserId() : draft.fromUserId();
        conversationRepository.ensureExists(draft.conversationId(), userA, userB);

        var existing = findExisting(draft);
        if (existing.isPresent()) {
            return existing.get();
        }

        long seq = seqAllocator.nextConversationSeq(draft.conversationId());
        UUID messageId = idGenerator.nextId();
        Instant now = Instant.now();

        PrivateMessageRecord message = new PrivateMessageRecord(
                draft.conversationId(),
                seq,
                messageId,
                draft.fromUserId(),
                draft.toUserId(),
                draft.content(),
                draft.clientMsgId(),
                now
        );
        privateMessageRepository.insert(message);

        // Sender has read their own outgoing message.
        readStateRepository.updateLastReadSeqMax(draft.conversationId(), draft.fromUserId(), seq);

        return message;
    }

    public Optional<PrivateMessageRecord> findExisting(PrivateMessageDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("message draft required");
        }
        return privateMessageRepository.findByIdempotency(
                draft.conversationId(),
                draft.fromUserId(),
                draft.clientMsgId()
        );
    }

    public record PrivateMessageDraft(
            UUID fromUserId,
            UUID toUserId,
            String conversationId,
            String content,
            String clientMsgId
    ) {
    }
}
