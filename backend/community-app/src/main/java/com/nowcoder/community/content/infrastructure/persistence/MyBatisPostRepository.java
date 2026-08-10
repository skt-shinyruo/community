package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;
import static com.nowcoder.community.content.exception.ContentErrorCode.POST_CONCURRENT_MODIFICATION;

@Repository
public class MyBatisPostRepository implements PostRepository {

    private final DiscussPostMapper discussPostMapper;
    private final UuidV7Generator idGenerator;

    public MyBatisPostRepository(DiscussPostMapper discussPostMapper, UuidV7Generator idGenerator) {
        this.discussPostMapper = Objects.requireNonNull(
                discussPostMapper, "discussPostMapper must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public UUID create(PostDraft draft) {
        DiscussPost post = new DiscussPost();
        post.setId(idGenerator.next());
        post.setUserId(draft.userId());
        post.setCategoryId(draft.categoryId());
        post.setTitle(draft.title());
        post.setType(0);
        post.setStatus(DiscussPost.STATUS_NORMAL);
        post.setCreateTime(draft.createTime());
        post.setCommentCount(0);
        post.setScore(0.0);
        post.setAggregateVersion(1L);
        discussPostMapper.insertDiscussPost(post);
        return post.getId();
    }

    @Override
    public PostSnapshot getRequiredSnapshot(UUID postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return snapshot(post);
    }

    @Override
    public void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime, long expectedVersion) {
        requireUpdated(discussPostMapper.updatePostMetaIfVersion(
                postId,
                title,
                categoryId,
                updateTime,
                expectedVersion
        ));
    }

    @Override
    public boolean markDeletedByAuthor(UUID postId, UUID authorUserId, Date deletedTime, long expectedVersion) {
        return deleteIfVersion(postId, authorUserId, "author_delete", deletedTime, expectedVersion, authorUserId);
    }

    @Override
    public void markTop(UUID postId, Date updateTime, long expectedVersion) {
        requireUpdated(discussPostMapper.updateTypeIfVersion(postId, 1, updateTime, expectedVersion));
    }

    @Override
    public void markWonderful(UUID postId, Date updateTime, long expectedVersion) {
        requireUpdated(discussPostMapper.updateStatusIfVersion(
                postId,
                DiscussPost.STATUS_WONDERFUL,
                updateTime,
                expectedVersion
        ));
    }

    @Override
    public boolean markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime, long expectedVersion) {
        return deleteIfVersion(postId, actorUserId, "admin_delete", deletedTime, expectedVersion, null);
    }

    private boolean deleteIfVersion(
            UUID postId,
            UUID actorUserId,
            String reason,
            Date deletedTime,
            long expectedVersion,
            UUID expectedAuthorUserId
    ) {
        int updated = discussPostMapper.updateModerationDeleteMetaIfVersion(
                postId,
                DiscussPost.STATUS_DELETED,
                actorUserId,
                reason,
                deletedTime,
                expectedVersion,
                expectedAuthorUserId
        );
        if (updated == 1) {
            return true;
        }
        DiscussPost current = discussPostMapper.selectDiscussPostById(postId);
        if (current == null || current.isDeleted()) {
            return false;
        }
        throw new BusinessException(POST_CONCURRENT_MODIFICATION);
    }

    private void requireUpdated(int updated) {
        if (updated != 1) {
            throw new BusinessException(POST_CONCURRENT_MODIFICATION);
        }
    }

    private PostSnapshot snapshot(DiscussPost post) {
        return new PostSnapshot(
                post.getId(),
                post.getUserId(),
                post.getType(),
                post.getStatus(),
                post.getCreateTime(),
                post.getUpdateTime(),
                post.getAggregateVersion()
        );
    }
}
