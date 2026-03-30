package com.nowcoder.community.notice.service;

import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.entity.Message;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeItemAssemblerTest {

    @Test
    void toLetterItemShouldProjectMessageFields() {
        NoticeItemAssembler assembler = new NoticeItemAssembler();
        Date createTime = new Date();
        Message message = new Message();
        message.setId(11);
        message.setFromId(3);
        message.setToId(7);
        message.setConversationId("3_7");
        message.setContent("hello");
        message.setStatus(1);
        message.setCreateTime(createTime);

        LetterItemResponse response = assembler.toLetterItem(message);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(11);
        assertThat(response.getFromId()).isEqualTo(3);
        assertThat(response.getToId()).isEqualTo(7);
        assertThat(response.getConversationId()).isEqualTo("3_7");
        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getStatus()).isEqualTo(1);
        assertThat(response.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void toLetterItemShouldReturnNullForNullMessage() {
        NoticeItemAssembler assembler = new NoticeItemAssembler();

        assertThat(assembler.toLetterItem(null)).isNull();
    }
}
