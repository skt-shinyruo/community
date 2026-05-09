package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;

import java.util.Date;
import java.util.UUID;

public interface PostRepository {

    UUID create(PostDraft draft);

    PostSnapshot getRequiredSnapshot(UUID postId);

    void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime);

    boolean markDeletedByAuthor(UUID postId, UUID authorUserId, Date deletedTime);

    void markTop(UUID postId);

    void markWonderful(UUID postId);

    boolean markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime);
}
