package com.nowcoder.community.notice.application.result;

public record NoticeTopicSummaryResult(
        String topic,
        NoticeItemResult latest,
        int noticeCount,
        int unreadCount
) {
}
