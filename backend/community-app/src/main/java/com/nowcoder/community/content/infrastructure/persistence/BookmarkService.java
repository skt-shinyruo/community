// 收藏服务：支持收藏/取消收藏、收藏状态查询与收藏列表分页。
package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.application.port.BookmarkContentPort;
import com.nowcoder.community.content.application.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.infrastructure.persistence.mapper.BookmarkMapper;
import com.nowcoder.community.content.domain.model.Comment;
import com.nowcoder.community.content.domain.model.DiscussPost;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class BookmarkService implements BookmarkContentPort {

    private final BookmarkMapper bookmarkMapper;
    private final PostService postService;
    private final CommentService commentService;
    private final TagService tagService;
    private final PostSummaryAssembler postSummaryAssembler;

    public BookmarkService(
            BookmarkMapper bookmarkMapper,
            PostService postService,
            CommentService commentService,
            TagService tagService,
            PostSummaryAssembler postSummaryAssembler
    ) {
        this.bookmarkMapper = bookmarkMapper;
        this.postService = postService;
        this.commentService = commentService;
        this.tagService = tagService;
        this.postSummaryAssembler = postSummaryAssembler;
    }

    @Override
    public void add(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        // 校验帖子存在且未删除
        postService.getById(postId);
        bookmarkMapper.insertBookmark(userId, postId, new Date());
    }

    @Override
    public void remove(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        bookmarkMapper.deleteBookmark(userId, postId);
    }

    @Override
    public boolean hasBookmarked(UUID userId, UUID postId) {
        if (userId == null || postId == null) {
            return false;
        }
        return bookmarkMapper.existsBookmark(userId, postId) > 0;
    }

    @Override
    public List<DiscussPost> listBookmarkedPosts(UUID userId, int page, int size) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return bookmarkMapper.selectBookmarkedPosts(userId, Pagination.safeOffset(p, s), s);
    }

    @Override
    public List<PostSummaryResult> listBookmarkedPostSummaries(UUID userId, int page, int size) {
        List<DiscussPost> posts = listBookmarkedPosts(userId, page, size);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<UUID, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<UUID, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .toList();
    }
}
