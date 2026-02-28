package com.nowcoder.community.message.api.dto;

public class NoticeTopicSummaryResponse {

    private String topic;
    private LetterItemResponse latest;
    private int noticeCount;
    private int unreadCount;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public LetterItemResponse getLatest() {
        return latest;
    }

    public void setLatest(LetterItemResponse latest) {
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
