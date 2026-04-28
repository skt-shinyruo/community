package com.nowcoder.community.content.domain.repository;

import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;

import java.util.Date;
import java.util.UUID;

public interface PostRepository {

    UUID create(PostDraft draft);

    PostSnapshot getRequiredSnapshot(UUID postId);

    void updateContent(UUID postId, String title, String content, UUID categoryId, Date updateTime);

    void markDeletedByAuthor(UUID postId, UUID authorUserId, Date deletedTime);

    void markTop(UUID postId);

    void markWonderful(UUID postId);

    void markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime);
}
