package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.repository.NoticeRepository;
import com.nowcoder.community.notice.infrastructure.persistence.dataobject.NoticeRecordDataObject;
import com.nowcoder.community.notice.infrastructure.persistence.mapper.NoticeMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class MyBatisNoticeRepository implements NoticeRepository {

    private final NoticeMapper noticeMapper;

    public MyBatisNoticeRepository(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Override
    public int insert(NoticeRecord notice) {
        return noticeMapper.insertNotice(NoticeRecordDataObject.from(notice));
    }

    @Override
    public List<NoticeRecord> findByUserAndTopic(UUID userId, String topic, int offset, int limit) {
        return new ArrayList<>(noticeMapper.selectNotices(userId, topic, offset, limit));
    }

    @Override
    public int count(UUID userId, String topic) {
        return noticeMapper.selectNoticeCount(userId, topic);
    }

    @Override
    public int unreadCount(UUID userId, String topic) {
        return noticeMapper.selectNoticeUnreadCount(userId, topic);
    }

    @Override
    public int markRead(UUID userId, List<UUID> ids, int status) {
        return noticeMapper.updateNoticesStatusForRecipient(ids, status, userId);
    }
}
