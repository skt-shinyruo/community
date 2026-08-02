package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;

import java.util.Date;
import java.util.UUID;

public interface PostRepository {

    UUID create(PostDraft draft);

    PostSnapshot getRequiredSnapshot(UUID postId);

    void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime, long expectedVersion);

    boolean markDeletedByAuthor(UUID postId, UUID authorUserId, Date deletedTime, long expectedVersion);

    void markTop(UUID postId, Date updateTime, long expectedVersion);

    void markWonderful(UUID postId, Date updateTime, long expectedVersion);

    boolean markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime, long expectedVersion);
}
