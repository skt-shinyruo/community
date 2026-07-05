package com.nowcoder.community.notice.application.result;

public record NoticeTopicSummaryResult(
        String noticeTopic,
        NoticeItemResult latest,
        int noticeCount,
        int unreadCount
) {
}
