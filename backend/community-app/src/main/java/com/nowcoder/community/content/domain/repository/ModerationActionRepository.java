package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.ModerationActionRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModerationActionRepository {

    ModerationActionRecord writeAction(UUID actorId, UUID reportId, String action, String reason, Integer durationSeconds);

    Optional<ModerationActionRecord> findByReportId(UUID reportId);

    List<ModerationActionRecord> listActions(UUID actorId, int page, int size);
}
