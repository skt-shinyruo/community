package com.nowcoder.community.im.core.domain.service;

import com.nowcoder.community.im.core.domain.repository.ConversationRepository;
import com.nowcoder.community.im.core.domain.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SeqAllocatorTest {

    @Autowired
    private SeqAllocator seqAllocator;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void nextRoomSeq_incrementsMonotonically() {
        UUID roomId = uuid(1001);
        roomRepository.insertRoom(roomId, "test");

        assertThat(seqAllocator.nextRoomSeq(roomId)).isEqualTo(1L);
        assertThat(seqAllocator.nextRoomSeq(roomId)).isEqualTo(2L);
    }

    @Test
    void nextConversationSeq_incrementsMonotonically() {
        UUID userId1 = uuid(1);
        UUID userId2 = uuid(2);
        String conversationId = userId1 + "_" + userId2;
        conversationRepository.ensureExists(conversationId, userId1, userId2);

        assertThat(seqAllocator.nextConversationSeq(conversationId)).isEqualTo(1L);
        assertThat(seqAllocator.nextConversationSeq(conversationId)).isEqualTo(2L);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
