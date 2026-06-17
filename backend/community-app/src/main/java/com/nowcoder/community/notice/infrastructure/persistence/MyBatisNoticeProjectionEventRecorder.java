package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.notice.application.NoticeProjectionEventRecorder;
import com.nowcoder.community.notice.infrastructure.persistence.mapper.NoticeProjectionEventMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MyBatisNoticeProjectionEventRecorder implements NoticeProjectionEventRecorder {

    private final NoticeProjectionEventMapper noticeProjectionEventMapper;

    public MyBatisNoticeProjectionEventRecorder(NoticeProjectionEventMapper noticeProjectionEventMapper) {
        this.noticeProjectionEventMapper = noticeProjectionEventMapper;
    }

    @Override
    public boolean tryRecord(String sourceEventId) {
        if (!StringUtils.hasText(sourceEventId)) {
            return false;
        }
        try {
            return noticeProjectionEventMapper.insert(sourceEventId.trim()) > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
