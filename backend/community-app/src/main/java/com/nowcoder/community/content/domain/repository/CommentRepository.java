package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.CommentDraft;
import com.nowcoder.community.content.domain.model.CommentSnapshot;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public interface CommentRepository {

    UUID create(CommentDraft draft);

    CommentSnapshot getRequiredSnapshot(UUID commentId);

    Optional<CommentSnapshot> findSnapshot(UUID commentId);

    Optional<CommentSnapshot> findActiveSnapshot(UUID commentId);

    void updateContent(UUID commentId, String content, Date updateTime);
}
