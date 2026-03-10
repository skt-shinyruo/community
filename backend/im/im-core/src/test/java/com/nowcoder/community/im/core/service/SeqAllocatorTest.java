package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.core.db.ConversationRepository;
import com.nowcoder.community.im.core.db.RoomRepository;
import com.nowcoder.community.im.core.db.SeqAllocator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        long roomId = 1001L;
        roomRepository.insertRoom(roomId, "test");

        assertThat(seqAllocator.nextRoomSeq(roomId)).isEqualTo(1L);
        assertThat(seqAllocator.nextRoomSeq(roomId)).isEqualTo(2L);
    }

    @Test
    void nextConversationSeq_incrementsMonotonically() {
        String conversationId = "1_2";
        conversationRepository.ensureExists(conversationId, 1, 2);

        assertThat(seqAllocator.nextConversationSeq(conversationId)).isEqualTo(1L);
        assertThat(seqAllocator.nextConversationSeq(conversationId)).isEqualTo(2L);
    }
}

