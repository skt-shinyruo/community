package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Repository
@ConditionalOnMissingBean(HotFeedProjectionGuard.class)
public class PassthroughHotFeedProjectionGuard implements HotFeedProjectionGuard {

    @Override
    public ProjectionAttempt tryBegin(UUID postId, String sourceEventId, long sourceVersion) {
        if (postId == null || !StringUtils.hasText(sourceEventId) || sourceVersion <= 0L) {
            return ProjectionAttempt.rejected(postId, sourceEventId, sourceVersion);
        }
        return ProjectionAttempt.accepted(postId, sourceEventId.trim(), sourceVersion, "passthrough");
    }

    @Override
    public boolean isCurrent(ProjectionAttempt attempt) {
        return attempt != null && attempt.accepted();
    }

    @Override
    public void commit(ProjectionAttempt attempt) {
    }

    @Override
    public void abort(ProjectionAttempt attempt) {
    }
}
