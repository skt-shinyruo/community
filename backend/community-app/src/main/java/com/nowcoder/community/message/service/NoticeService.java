package com.nowcoder.community.message.service;

import com.nowcoder.community.message.mapper.MessageMapper;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NoticeService {

    public static final int SYSTEM_NOTICE_SENDER_ID = Message.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    private final MessageMapper messageMapper;
    private final MessageItemAssembler messageItemAssembler;

    public NoticeService(MessageMapper messageMapper, MessageItemAssembler messageItemAssembler) {
        this.messageMapper = messageMapper;
        this.messageItemAssembler = messageItemAssembler;
    }

    public void createNotice(int toUserId, String topic, String contentJson) {
        Message msg = new Message();
        msg.setFromId(SYSTEM_NOTICE_SENDER_ID);
        msg.setToId(toUserId);
        msg.setConversationId(topic);
        msg.setContent(contentJson);
        msg.setStatus(STATUS_UNREAD);
        msg.setCreateTime(new Date());
        messageMapper.insertMessage(msg);
    }

    public List<Message> listNotices(int userId, String topic, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return messageMapper.selectNotices(userId, topic, offset, s);
    }

    public List<LetterItemResponse> listNoticeItems(int userId, String topic, int page, int size) {
        List<Message> list = listNotices(userId, topic, page, size);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(messageItemAssembler::toLetterItem).toList();
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
            r.setLatest(latest == null || latest.isEmpty() ? null : messageItemAssembler.toLetterItem(latest.get(0)));
            r.setNoticeCount(messageMapper.selectNoticeCount(userId, topic));
            r.setUnreadCount(messageMapper.selectNoticeUnreadCount(userId, topic));
            return r;
        }).toList();
    }
}
