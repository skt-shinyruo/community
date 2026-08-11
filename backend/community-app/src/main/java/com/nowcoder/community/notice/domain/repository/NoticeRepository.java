package com.nowcoder.community.notice.domain.repository;

import com.nowcoder.community.notice.domain.model.NoticeRecord;
import com.nowcoder.community.notice.domain.model.NoticeTopicSummary;

import java.util.List;
import java.util.UUID;

public interface NoticeRepository {

    int insert(NoticeRecord notice);

    List<NoticeRecord> findByUserAndTopic(UUID userId, String topic, int offset, int limit);

    int count(UUID userId, String topic);

    int unreadCount(UUID userId, String topic);

    default List<NoticeTopicSummary> summarizeByUserAndTopics(UUID userId, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        return topics.stream().map(topic -> {
            List<NoticeRecord> latest = findByUserAndTopic(userId, topic, 0, 1);
            return new NoticeTopicSummary(
                    latest == null || latest.isEmpty() ? null : latest.get(0),
                    count(userId, topic),
                    unreadCount(userId, topic)
            );
        }).toList();
    }

    int markUnreadAsRead(UUID userId, List<UUID> ids);

    int revokeLikeNotice(UUID recipientUserId, String relationKey, int revokedStatus);
}
