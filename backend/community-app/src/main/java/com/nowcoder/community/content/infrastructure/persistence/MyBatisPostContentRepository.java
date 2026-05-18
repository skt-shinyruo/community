package com.nowcoder.community.content.infrastructure.persistence;

// 帖子领域服务：封装帖子查询与状态/计数更新等操作。
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.id.UuidV7Generator;
import com.nowcoder.community.content.domain.repository.PostContentRepository;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.content.infrastructure.persistence.mapper.DiscussPostMapper;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.content.exception.ContentErrorCode.POST_NOT_FOUND;

@Service
public class MyBatisPostContentRepository implements PostContentRepository {

    public static final int ORDER_LATEST = 0;
    public static final int ORDER_HOT = 1;

    private final DiscussPostMapper discussPostMapper;
    private final UuidV7Generator idGenerator;

    @Autowired
    public MyBatisPostContentRepository(DiscussPostMapper discussPostMapper) {
        this(discussPostMapper, new UuidV7Generator());
    }

    MyBatisPostContentRepository(DiscussPostMapper discussPostMapper, UuidV7Generator idGenerator) {
        this.discussPostMapper = discussPostMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<DiscussPost> listPosts(int page, int size, int orderMode) {
        return listPosts(page, size, orderMode, null, null);
    }

    @Override
    public List<DiscussPost> listPosts(int page, int size, int orderMode, UUID categoryId, String tag) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        String safeTag = tag == null ? null : tag.trim();
        if (safeTag != null && safeTag.isBlank()) {
            safeTag = null;
        }
        return discussPostMapper.selectDiscussPosts(null, categoryId, null, safeTag, Pagination.safeOffset(p, s), s, orderMode);
    }

    @Override
    public List<DiscussPost> listPostsByUser(UUID userId, int page, int size) {
        if (userId == null) {
            return List.of();
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return discussPostMapper.selectDiscussPosts(userId, null, null, null, Pagination.safeOffset(p, s), s, ORDER_LATEST);
    }

    @Override
    public List<DiscussPost> listPostsByIds(List<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<UUID, Boolean> orderedIds = new LinkedHashMap<>();
        for (UUID rawId : postIds) {
            if (rawId == null) continue;
            orderedIds.putIfAbsent(rawId, Boolean.TRUE);
            if (orderedIds.size() >= 200) break;
        }
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        List<DiscussPost> rows = discussPostMapper.selectDiscussPostsByIds(List.copyOf(orderedIds.keySet()));
        Map<UUID, DiscussPost> byId = new LinkedHashMap<>();
        for (DiscussPost post : rows) {
            if (post == null || post.getId() == null) continue;
            byId.put(post.getId(), post);
        }

        return orderedIds.keySet().stream()
                .map(byId::get)
                .filter(post -> post != null)
                .toList();
    }

    @Override
    public List<DiscussPost> scanAfterId(UUID afterId, int limit) {
        int safeLimit = limit <= 0 ? 500 : Math.min(1000, Math.max(1, limit));
        List<DiscussPost> posts = discussPostMapper.selectDiscussPostsAfterId(afterId, safeLimit);
        return posts == null ? List.of() : posts;
    }

    @Override
    public DiscussPost getById(UUID postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        if (post.isDeleted()) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return post;
    }

    @Override
    public DiscussPost getByIdAllowDeleted(UUID postId) {
        DiscussPost post = discussPostMapper.selectDiscussPostById(postId);
        if (post == null) {
            throw new BusinessException(POST_NOT_FOUND);
        }
        return post;
    }

    @Override
    public List<DiscussPost> listSubscribedPosts(
            UUID userId,
            List<UUID> subscribedCategoryIds,
            int page,
            int size,
            int orderMode,
            UUID categoryId,
            String tag
    ) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));

        if (userId == null) {
            return List.of();
        }
        if (subscribedCategoryIds == null || subscribedCategoryIds.isEmpty()) {
            return List.of();
        }

        String safeTag = tag == null ? null : tag.trim();
        if (safeTag != null && safeTag.isBlank()) {
            safeTag = null;
        }
        return discussPostMapper.selectDiscussPosts(null, categoryId, subscribedCategoryIds, safeTag, Pagination.safeOffset(p, s), s, orderMode);
    }

    @Override
    public UUID create(DiscussPost post) {
        if (post.getId() == null) {
            post.setId(idGenerator.next());
        }
        discussPostMapper.insertDiscussPost(post);
        return post.getId();
    }

    @Override
    public void updateCommentCount(UUID postId, int commentCount) {
        discussPostMapper.updateCommentCount(postId, commentCount);
    }

    @Override
    public void incrementCommentCount(UUID postId, int delta) {
        discussPostMapper.incrementCommentCount(postId, delta);
    }

    @Override
    public void updateType(UUID postId, int type) {
        discussPostMapper.updateType(postId, type);
    }

    @Override
    public void updateStatus(UUID postId, int status) {
        discussPostMapper.updateStatus(postId, status);
    }

    @Override
    public void updateScore(UUID postId, double score) {
        discussPostMapper.updateScore(postId, score);
    }

    @Override
    public void updatePostMeta(UUID postId, String title, UUID categoryId, Date updateTime) {
        discussPostMapper.updatePostMeta(postId, title, categoryId, updateTime);
    }

    @Override
    public void updateModerationDeleteMeta(UUID postId, int status, UUID deletedBy, String deletedReason, Date deletedTime) {
        discussPostMapper.updateModerationDeleteMeta(postId, status, deletedBy, deletedReason, deletedTime);
    }
}
