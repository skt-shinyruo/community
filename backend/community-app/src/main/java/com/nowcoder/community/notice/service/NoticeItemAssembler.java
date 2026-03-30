package com.nowcoder.community.notice.service;

import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.entity.Message;
import org.springframework.stereotype.Service;

@Service
public class NoticeItemAssembler {

    public LetterItemResponse toLetterItem(Message message) {
        if (message == null) {
            return null;
        }
        LetterItemResponse response = new LetterItemResponse();
        response.setId(message.getId());
        response.setFromId(message.getFromId());
        response.setToId(message.getToId());
        response.setConversationId(message.getConversationId());
        response.setContent(message.getContent());
        response.setStatus(message.getStatus());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}
