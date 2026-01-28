package com.nowcoder.community.content.service;

// 帖子领域服务：封装帖子查询与状态/计数更新等操作。
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.dao.DiscussPostMapper;
import com.nowcoder.community.content.entity.DiscussPost;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.NOT_FOUND;

@Service
public class PostService {

    public static final int ORDER_LATEST = 0;
    public static final int ORDER_HOT = 1;

    private final DiscussPostMapper discussPostMapper;

    public PostService(DiscussPostMapper discussPostMapper) {
        this.discussPostMapper = discussPostMapper;
    }

    public List<DiscussPost> listPosts(int page, int size, int orderMode) {
        return listPosts(page, size, orderMode, null, null);
    }

    public List<DiscussPost> listPosts(int page, int size, int orderMode, Integer categoryId, String tag) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        String safeTag = tag == null ? null : tag.trim();
        if (safeTag != null && safeTag.isBlank()) {
            safeTag = null;
        }
        Integer safeCategoryId = (categoryId != null && categoryId > 0) ? categoryId : null;
        return discussPostMapper.selectDiscussPosts(0, safeCategoryId, null, safeTag, p * s, s, orderMode);
    }

    public DiscussPost getById(int postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(NOT_FOUND, "帖子不存在");
        }
        if (post.getStatus() == 2) {
            throw new BusinessException(NOT_FOUND, "帖子不存在");
        }
        return post;
    }

    public DiscussPost getByIdAllowDeleted(int postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(NOT_FOUND, "帖子不存在");
        }
        return post;
    }

    public List<DiscussPost> listSubscribedPosts(
            int userId,
            List<Integer> subscribedCategoryIds,
            int page,
            int size,
            int orderMode,
            Integer categoryId,
            String tag
    ) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));

        if (userId <= 0) {
            return List.of();
        }
        if (subscribedCategoryIds == null || subscribedCategoryIds.isEmpty()) {
            return List.of();
        }

        String safeTag = tag == null ? null : tag.trim();
        if (safeTag != null && safeTag.isBlank()) {
            safeTag = null;
        }
        Integer safeCategoryId = (categoryId != null && categoryId > 0) ? categoryId : null;
        return discussPostMapper.selectDiscussPosts(0, safeCategoryId, subscribedCategoryIds, safeTag, p * s, s, orderMode);
    }

    public int create(DiscussPost post) {
        discussPostMapper.insertDiscussPost(post);
        return post.getId();
    }

    public void updateCommentCount(int postId, int commentCount) {
        discussPostMapper.updateCommentCount(postId, commentCount);
    }

    public void incrementCommentCount(int postId, int delta) {
        discussPostMapper.incrementCommentCount(postId, delta);
    }

    public void updateType(int postId, int type) {
        discussPostMapper.updateType(postId, type);
    }

    public void updateStatus(int postId, int status) {
        discussPostMapper.updateStatus(postId, status);
    }

    public void updateScore(int postId, double score) {
        discussPostMapper.updateScore(postId, score);
    }

    public void updatePostContent(int postId, String title, String content, Integer categoryId, Date updateTime) {
        discussPostMapper.updatePostContent(postId, title, content, categoryId, updateTime);
    }

    public void updateModerationDeleteMeta(int postId, int status, int deletedBy, String deletedReason, Date deletedTime) {
        discussPostMapper.updateModerationDeleteMeta(postId, status, deletedBy, deletedReason, deletedTime);
    }
}
