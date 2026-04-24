package com.nowcoder.community.notice.service;

import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.dto.NoticeTopicSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NoticeApplicationService {

    private final NoticeService noticeService;

    public NoticeApplicationService(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    public List<NoticeItemResponse> listNoticeItems(UUID userId, String topic, Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return noticeService.listNoticeItems(userId, topic, p, s);
    }

    public int unreadCount(UUID userId, String topic) {
        return noticeService.unreadCount(userId, topic);
    }

    public List<NoticeTopicSummaryResponse> topicSummary(UUID userId) {
        return noticeService.topicSummary(userId);
    }

    public void markRead(UUID userId, List<UUID> ids) {
        noticeService.markRead(userId, ids);
    }
}
