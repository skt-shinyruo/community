package com.nowcoder.community.notice.dto;

public class NoticeTopicSummaryResponse {

    private String topic;
    private NoticeItemResponse latest;
    private int noticeCount;
    private int unreadCount;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public NoticeItemResponse getLatest() {
        return latest;
    }

    public void setLatest(NoticeItemResponse latest) {
        this.latest = latest;
    }

    public int getNoticeCount() {
        return noticeCount;
    }

    public void setNoticeCount(int noticeCount) {
        this.noticeCount = noticeCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }
}
