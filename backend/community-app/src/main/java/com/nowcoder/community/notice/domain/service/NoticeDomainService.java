package com.nowcoder.community.notice.domain.service;

import java.util.UUID;

public final class NoticeDomainService {

    public static final int STATUS_UNREAD = 0;
    public static final int STATUS_READ = 1;

    public int pageOrDefault(Integer page) {
        return page == null ? 0 : Math.max(0, page);
    }

    public int sizeOrDefault(Integer size) {
        return size == null ? 10 : Math.min(50, Math.max(1, size));
    }

    public void validateCreate(UUID toUserId, String topic, String contentJson) {
        if (toUserId == null || topic == null || topic.isBlank() || contentJson == null || contentJson.isBlank()) {
            throw new IllegalArgumentException("notice recipient, topic and content are required");
        }
    }
}
