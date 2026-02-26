package com.nowcoder.community.message.api.dto;

import com.nowcoder.community.message.entity.Message;

public class NoticeTopicSummaryResponse {

    private String topic;
    private Message latest;
    private int noticeCount;
    private int unreadCount;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Message getLatest() {
        return latest;
    }

    public void setLatest(Message latest) {
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

