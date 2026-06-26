package com.nowcoder.community.notice.infrastructure.persistence.dataobject;

import com.nowcoder.community.notice.domain.model.NoticeRecord;

public class NoticeRecordDataObject extends NoticeRecord {

    public static NoticeRecordDataObject from(NoticeRecord notice) {
        NoticeRecordDataObject row = new NoticeRecordDataObject();
        row.setId(notice.getId());
        row.setSenderUserId(notice.getSenderUserId());
        row.setRecipientUserId(notice.getRecipientUserId());
        row.setTopic(notice.getTopic());
        row.setContent(notice.getContent());
        row.setSourceEventType(notice.getSourceEventType());
        row.setSourceRelationKey(notice.getSourceRelationKey());
        row.setStatus(notice.getStatus());
        row.setCreateTime(notice.getCreateTime());
        return row;
    }
}
