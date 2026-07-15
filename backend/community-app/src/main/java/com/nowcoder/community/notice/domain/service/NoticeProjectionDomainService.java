package com.nowcoder.community.notice.domain.service;

import com.nowcoder.community.notice.domain.model.NoticeProjection;

public final class NoticeProjectionDomainService {

    public boolean shouldProject(NoticeProjection projection) {
        return projection != null
                && projection.toUserId() != null
                && projection.topic() != null
                && !projection.topic().isBlank()
                && projection.content() != null;
    }
}
