package com.nowcoder.community.notice.domain.model;

public record NoticeTopicSummary(
        NoticeRecord latest,
        int noticeCount,
        int unreadCount
) {
}
