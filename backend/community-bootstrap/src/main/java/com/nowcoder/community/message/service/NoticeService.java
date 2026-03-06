package com.nowcoder.community.message.service;

import com.nowcoder.community.message.dao.MessageMapper;
import com.nowcoder.community.message.api.dto.LetterItemResponse;
import com.nowcoder.community.message.api.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NoticeService {

    public static final int SYSTEM_USER_ID = 1;
    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    private final MessageMapper messageMapper;

    public NoticeService(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    public void createNotice(int toUserId, String topic, String contentJson) {
        Message msg = new Message();
        msg.setFromId(SYSTEM_USER_ID);
        msg.setToId(toUserId);
        msg.setConversationId(topic);
        msg.setContent(contentJson);
        msg.setStatus(STATUS_UNREAD);
        msg.setCreateTime(new Date());
        messageMapper.insertMessage(msg);
    }

    public List<Message> listNotices(int userId, String topic, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        return messageMapper.selectNotices(userId, topic, offset, size);
    }

    public int unreadCount(int userId, String topic) {
        return messageMapper.selectNoticeUnreadCount(userId, topic);
    }

    public void markRead(int userId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        messageMapper.updateNoticesStatusForRecipient(ids, STATUS_READ, userId);
    }

    public List<NoticeTopicSummaryResponse> topicSummary(int userId) {
        // v1 topics：comment/like/follow + moderation（治理通知）
        return List.of("comment", "like", "follow", "moderation").stream().map(topic -> {
            NoticeTopicSummaryResponse r = new NoticeTopicSummaryResponse();
            r.setTopic(topic);
            List<Message> latest = messageMapper.selectNotices(userId, topic, 0, 1);
            r.setLatest(latest == null || latest.isEmpty() ? null : toLetterItem(latest.get(0)));
            r.setNoticeCount(messageMapper.selectNoticeCount(userId, topic));
            r.setUnreadCount(messageMapper.selectNoticeUnreadCount(userId, topic));
            return r;
        }).toList();
    }

    private LetterItemResponse toLetterItem(Message m) {
        if (m == null) {
            return null;
        }
        LetterItemResponse r = new LetterItemResponse();
        r.setId(m.getId());
        r.setFromId(m.getFromId());
        r.setToId(m.getToId());
        r.setConversationId(m.getConversationId());
        r.setContent(m.getContent());
        r.setStatus(m.getStatus());
        r.setCreateTime(m.getCreateTime());
        return r;
    }
}
