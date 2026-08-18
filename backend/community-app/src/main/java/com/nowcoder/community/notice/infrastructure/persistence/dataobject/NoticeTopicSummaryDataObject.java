package com.nowcoder.community.notice.infrastructure.persistence.dataobject;

import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.model.NoticeTopicSummary;

public class NoticeTopicSummaryDataObject extends NoticeRecord {

    private int noticeCount;
    private int unreadCount;

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

    public NoticeTopicSummary toDomainSummary() {
        NoticeRecord latest = new NoticeRecord();
        latest.setId(getId());
        latest.setSenderUserId(getSenderUserId());
        latest.setRecipientUserId(getRecipientUserId());
        latest.setTopic(getTopic());
        latest.setContent(getContent());
        latest.setSourceEventType(getSourceEventType());
        latest.setSourceRelationKey(getSourceRelationKey());
        latest.setStatus(getStatus());
        latest.setCreateTime(getCreateTime());
        return new NoticeTopicSummary(latest, noticeCount, unreadCount);
    }
}
