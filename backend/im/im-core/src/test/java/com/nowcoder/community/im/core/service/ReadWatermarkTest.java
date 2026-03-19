package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.core.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.repository.RoomReadStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReadWatermarkTest {

    @Autowired
    private RoomReadStateRepository roomReadStateRepository;

    @Autowired
    private ConversationReadStateRepository conversationReadStateRepository;

    @Test
    void updateLastReadSeqMax_neverDecreases_room() {
        long roomId = 1001L;
        int userId = 1;
        roomReadStateRepository.updateLastReadSeqMax(roomId, userId, 5L);
        roomReadStateRepository.updateLastReadSeqMax(roomId, userId, 3L);
        assertThat(roomReadStateRepository.getLastReadSeq(roomId, userId)).isEqualTo(5L);
    }

    @Test
    void updateLastReadSeqMax_neverDecreases_conversation() {
        String conversationId = "1_2";
        int userId = 1;
        conversationReadStateRepository.updateLastReadSeqMax(conversationId, userId, 10L);
        conversationReadStateRepository.updateLastReadSeqMax(conversationId, userId, 7L);
        assertThat(conversationReadStateRepository.getLastReadSeq(conversationId, userId)).isEqualTo(10L);
    }
}

