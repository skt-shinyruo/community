package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.core.db.ConversationReadStateRepository;
import com.nowcoder.community.im.core.db.PrivateMessageRepository;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrivateMessageServiceTest {

    @Autowired
    private PrivateMessageService privateMessageService;

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private ConversationReadStateRepository readStateRepository;

    @Test
    void persist_isIdempotentByClientMsgId() {
        int fromUserId = 1;
        int toUserId = 2;
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);

        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-1",
                "c1",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );

        var e1 = privateMessageService.persist(cmd);
        var e2 = privateMessageService.persist(cmd);

        assertThat(e2.messageId()).isEqualTo(e1.messageId());
        assertThat(e2.seq()).isEqualTo(e1.seq());

        List<PrivateMessageRepository.PrivateMessageRow> rows =
                privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_marksSenderReadWatermarkToSeq() {
        int fromUserId = 1;
        int toUserId = 2;
        String conversationId = ConversationIdSupport.conversationId(fromUserId, toUserId);

        SendPrivateTextCommandV1 cmd = new SendPrivateTextCommandV1(
                "req-2",
                "c2",
                fromUserId,
                toUserId,
                conversationId,
                "hello",
                System.currentTimeMillis()
        );

        var e = privateMessageService.persist(cmd);
        assertThat(readStateRepository.getLastReadSeq(conversationId, fromUserId)).isEqualTo(e.seq());
    }
}

