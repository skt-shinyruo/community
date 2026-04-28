package com.nowcoder.community.notice.domain.repository;

import com.nowcoder.community.notice.domain.model.NoticeRecord;

import java.util.List;
import java.util.UUID;

public interface NoticeRepository {

    int insert(NoticeRecord notice);

    List<NoticeRecord> findByUserAndTopic(UUID userId, String topic, int offset, int limit);

    int count(UUID userId, String topic);

    int unreadCount(UUID userId, String topic);

    int markRead(UUID userId, List<UUID> ids, int status);
}
