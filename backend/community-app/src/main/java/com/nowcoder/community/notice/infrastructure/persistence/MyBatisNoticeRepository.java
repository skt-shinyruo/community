package com.nowcoder.community.notice.infrastructure.persistence;

import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.model.NoticeTopicSummary;
import com.nowcoder.community.notice.domain.repository.NoticeRepository;
import com.nowcoder.community.notice.domain.service.NoticeDomainService;
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
    public List<NoticeTopicSummary> summarizeByUserAndTopics(UUID userId, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return noticeMapper.selectTopicSummaries(userId, topics).stream()
                .map(row -> row.toDomainSummary())
                .toList();
    }

    @Override
    public int markUnreadAsRead(UUID userId, List<UUID> ids) {
        return noticeMapper.updateNoticesStatusForRecipient(
                ids, NoticeDomainService.STATUS_UNREAD, NoticeDomainService.STATUS_READ, userId);
    }

    @Override
    public int revokeLikeNotice(UUID recipientUserId, String relationKey, int revokedStatus) {
        return noticeMapper.revokeLikeNotice(
                recipientUserId,
                com.nowcoder.community.notice.domain.model.NoticeTopic.LIKE,
                com.nowcoder.community.social.contracts.event.SocialEventTypes.LIKE_CREATED,
                relationKey,
                com.nowcoder.community.notice.application.NoticeApplicationService.STATUS_UNREAD,
                com.nowcoder.community.notice.application.NoticeApplicationService.STATUS_READ,
                revokedStatus
        );
    }
}
