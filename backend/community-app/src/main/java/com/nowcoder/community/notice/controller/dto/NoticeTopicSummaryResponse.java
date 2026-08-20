package com.nowcoder.community.notice.controller.dto;

public record NoticeTopicSummaryResponse(
        String topic,
        NoticeItemResponse latest,
        int noticeCount,
        int unreadCount
) {
}
