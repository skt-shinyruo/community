package com.nowcoder.community.message.service;

import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.api.dto.ConversationItemResponse;
import com.nowcoder.community.message.api.dto.UserSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.dto.UserSummary;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class PrivateMessageService {

    private final MessageMapper messageMapper;
    private final UserServiceClient userServiceClient;

    public PrivateMessageService(MessageMapper messageMapper, UserServiceClient userServiceClient) {
        this.messageMapper = messageMapper;
        this.userServiceClient = userServiceClient;
    }

    public List<Message> listConversations(int userId, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectConversations(userId, offset, size);
    }

    public List<ConversationItemResponse> listConversationItems(int userId, int page, int size) {
        List<Message> latest = listConversations(userId, page, size);
        return latest.stream().map(m -> {
            ConversationItemResponse item = new ConversationItemResponse();
            item.setConversationId(m.getConversationId());
            item.setLastMessage(m);
            item.setLetterCount(messageMapper.selectLetterCount(m.getConversationId()));
            item.setUnreadCount(messageMapper.selectLetterUnreadCount(userId, m.getConversationId()));

            int targetId = m.getFromId() == userId ? m.getToId() : m.getFromId();
            UserSummary target = userServiceClient.safeGetUser(targetId);
            if (target != null && target.getId() > 0) {
                UserSummaryResponse tu = new UserSummaryResponse();
                tu.setId(target.getId());
                tu.setUsername(target.getUsername());
                tu.setHeaderUrl(target.getHeaderUrl());
                item.setTargetUser(tu);
            }
            return item;
        }).toList();
    }

    public List<Message> listLetters(String conversationId, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectLetters(conversationId, offset, size);
    }

    public int unreadCount(int userId, String conversationId) {
        return messageMapper.selectLetterUnreadCount(userId, conversationId);
    }

    public void send(int fromId, int toId, String content) {
        Message msg = new Message();
        msg.setFromId(fromId);
        msg.setToId(toId);
        msg.setConversationId(conversationId(fromId, toId));
        msg.setContent(content);
        msg.setStatus(NoticeService.STATUS_UNREAD);
        msg.setCreateTime(new Date());
        messageMapper.insertMessage(msg);
    }

    public void markRead(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        messageMapper.updateStatus(ids, NoticeService.STATUS_READ);
    }

    private String conversationId(int fromId, int toId) {
        int small = Math.min(fromId, toId);
        int large = Math.max(fromId, toId);
        return small + "_" + large;
    }
}
