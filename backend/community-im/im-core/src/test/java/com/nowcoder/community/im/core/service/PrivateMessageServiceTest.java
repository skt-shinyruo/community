package com.nowcoder.community.im.core.service;

import com.nowcoder.community.im.common.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.core.repository.ConversationReadStateRepository;
import com.nowcoder.community.im.core.repository.PrivateMessageRepository;
import com.nowcoder.community.im.core.support.ConversationIdSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
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
        assertThat(e1.requestId()).isEqualTo("req-1");
        assertThat(e1.clientMsgId()).isEqualTo("c1");

        List<PrivateMessageRepository.PrivateMessageRow> rows =
                privateMessageRepository.listAfterSeq(conversationId, 0, 100);
        assertThat(rows).hasSize(1);
    }

    @Test
    void persist_marksSenderReadWatermarkToSeq() {
        UUID fromUserId = uuid(1);
        UUID toUserId = uuid(2);
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
        assertThat(e.requestId()).isEqualTo("req-2");
        assertThat(e.clientMsgId()).isEqualTo("c2");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
