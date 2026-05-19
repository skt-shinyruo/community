package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;

import java.util.UUID;

/**
 * Allocates per-room / per-conversation monotonic seq.
 *
 * <p>Must be called within a transaction to ensure correctness.</p>
 */
public class SeqAllocator {

    private final RoomRepository roomRepository;
    private final ConversationRepository conversationRepository;

    public SeqAllocator(RoomRepository roomRepository, ConversationRepository conversationRepository) {
        this.roomRepository = roomRepository;
        this.conversationRepository = conversationRepository;
    }

    public long nextRoomSeq(UUID roomId) {
        long current = roomRepository.selectLastSeqForUpdate(roomId);
        long next = current + 1;
        roomRepository.updateLastSeq(roomId, next);
        return next;
    }

    public long nextConversationSeq(String conversationId) {
        long current = conversationRepository.selectLastSeqForUpdate(conversationId);
        long next = current + 1;
        conversationRepository.updateLastSeq(conversationId, next);
        return next;
    }
}
