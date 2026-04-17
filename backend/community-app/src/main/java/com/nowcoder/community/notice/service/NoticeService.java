package com.nowcoder.community.notice.service;

import com.nowcoder.community.infra.pagination.Pagination;
import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.notice.entity.NoticeRecord;
import com.nowcoder.community.notice.mapper.NoticeMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class NoticeService {

    public static final int SYSTEM_NOTICE_SENDER_ID = NoticeRecord.SYSTEM_NOTICE_SENDER_ID;
    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    private final NoticeMapper noticeMapper;

    public NoticeService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    public void createNotice(int toUserId, String topic, String contentJson) {
        NoticeRecord notice = new NoticeRecord();
        notice.setSenderUserId(SYSTEM_NOTICE_SENDER_ID);
        notice.setRecipientUserId(toUserId);
        notice.setTopic(topic);
        notice.setContent(contentJson);
        notice.setStatus(STATUS_UNREAD);
        notice.setCreateTime(new Date());
        noticeMapper.insertNotice(notice);
    }

    public List<NoticeRecord> listNotices(int userId, String topic, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        int offset = Pagination.safeOffset(p, s);
        return noticeMapper.selectNotices(userId, topic, offset, s);
    }

    public List<NoticeItemResponse> listNoticeItems(int userId, String topic, int page, int size) {
        List<NoticeRecord> list = listNotices(userId, topic, page, size);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(this::toNoticeItemResponse).toList();
    }

    public int unreadCount(int userId, String topic) {
        return noticeMapper.selectNoticeUnreadCount(userId, topic);
    }

    public void markRead(int userId, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        noticeMapper.updateNoticesStatusForRecipient(ids, STATUS_READ, userId);
    }

    public List<NoticeTopicSummaryResponse> topicSummary(int userId) {
        // v1 topics：comment/like/follow + moderation（治理通知）
        return List.of("comment", "like", "follow", "moderation").stream().map(topic -> {
            NoticeTopicSummaryResponse r = new NoticeTopicSummaryResponse();
            r.setTopic(topic);
            List<NoticeRecord> latest = noticeMapper.selectNotices(userId, topic, 0, 1);
            r.setLatest(latest == null || latest.isEmpty() ? null : toNoticeItemResponse(latest.get(0)));
            r.setNoticeCount(noticeMapper.selectNoticeCount(userId, topic));
            r.setUnreadCount(noticeMapper.selectNoticeUnreadCount(userId, topic));
            return r;
        }).toList();
    }

    private NoticeItemResponse toNoticeItemResponse(NoticeRecord notice) {
        NoticeItemResponse response = new NoticeItemResponse();
        response.setId(notice.getId());
        response.setSenderUserId(notice.getSenderUserId());
        response.setRecipientUserId(notice.getRecipientUserId());
        response.setTopic(notice.getTopic());
        response.setContent(notice.getContent());
        response.setStatus(notice.getStatus());
        response.setCreateTime(notice.getCreateTime());
        return response;
    }
}
