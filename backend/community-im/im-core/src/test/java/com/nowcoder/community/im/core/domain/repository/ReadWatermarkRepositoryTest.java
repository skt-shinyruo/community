package com.nowcoder.community.im.core.domain.repository;

import com.nowcoder.community.im.core.support.ConversationIdSupport;
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
class ReadWatermarkRepositoryTest {

    @Autowired
    private RoomReadStateRepository roomReadStateRepository;

    @Autowired
    private ConversationReadStateRepository conversationReadStateRepository;

    @Test
    void updateLastReadSeqMax_neverDecreases_room() {
        UUID roomId = uuid(1001);
        UUID userId = uuid(1);
        roomReadStateRepository.updateLastReadSeqMax(roomId, userId, 5L);
        roomReadStateRepository.updateLastReadSeqMax(roomId, userId, 3L);
        assertThat(roomReadStateRepository.getLastReadSeq(roomId, userId)).isEqualTo(5L);
    }

    @Test
    void updateLastReadSeqMax_neverDecreases_conversation() {
        UUID userId = uuid(1);
        String conversationId = ConversationIdSupport.conversationId(userId, uuid(2));
        conversationReadStateRepository.updateLastReadSeqMax(conversationId, userId, 10L);
        conversationReadStateRepository.updateLastReadSeqMax(conversationId, userId, 7L);
        assertThat(conversationReadStateRepository.getLastReadSeq(conversationId, userId)).isEqualTo(10L);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
