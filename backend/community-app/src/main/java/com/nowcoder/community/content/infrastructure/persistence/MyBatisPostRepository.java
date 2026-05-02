package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.model.PostDraft;
import com.nowcoder.community.content.domain.model.PostSnapshot;
import com.nowcoder.community.content.domain.repository.PostRepository;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Repository
public class MyBatisPostRepository implements PostRepository {

    private final DiscussPostMapper discussPostMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisPostRepository(DiscussPostMapper discussPostMapper) {
        this(discussPostMapper, new UuidV7Generator());
    }

    MyBatisPostRepository(DiscussPostMapper discussPostMapper, UuidV7Generator idGenerator) {
        this.discussPostMapper = discussPostMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public UUID create(PostDraft draft) {
        DiscussPost post = new DiscussPost();
        post.setId(idGenerator.next());
        post.setUserId(draft.userId());
        post.setCategoryId(draft.categoryId());
        post.setTitle(draft.title());
        post.setContent(draft.content());
        post.setType(0);
        post.setStatus(0);
        post.setCreateTime(draft.createTime());
        post.setCommentCount(0);
        post.setScore(0.0);
        discussPostMapper.insertDiscussPost(post);
        return post.getId();
    }

    @Override
    public PostSnapshot getRequiredSnapshot(UUID postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return new PostSnapshot(post.getId(), post.getUserId(), post.getStatus(), post.getCreateTime());
    }

    @Override
    public void updateContent(UUID postId, String title, String content, UUID categoryId, Date updateTime) {
        discussPostMapper.updatePostContent(postId, title, content, categoryId, updateTime);
    }

    @Override
    public boolean markDeletedByAuthor(UUID postId, UUID authorUserId, Date deletedTime) {
        return discussPostMapper.updateModerationDeleteMeta(postId, 2, authorUserId, "author_delete", deletedTime) > 0;
    }

    @Override
    public void markTop(UUID postId) {
        discussPostMapper.updateType(postId, 1);
    }

    @Override
    public void markWonderful(UUID postId) {
        discussPostMapper.updateStatus(postId, 1);
    }

    @Override
    public boolean markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime) {
        return discussPostMapper.updateModerationDeleteMeta(postId, 2, actorUserId, "admin_delete", deletedTime) > 0;
    }
}
