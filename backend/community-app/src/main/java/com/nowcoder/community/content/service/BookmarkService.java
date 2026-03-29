// 收藏服务：支持收藏/取消收藏、收藏状态查询与收藏列表分页。
package com.nowcoder.community.content.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.assembler.PostSummaryAssembler;
import com.nowcoder.community.content.mapper.BookmarkMapper;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.infra.pagination.Pagination;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class BookmarkService {

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

    public void add(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        // 校验帖子存在且未删除
        postService.getById(postId);
        bookmarkMapper.insertBookmark(userId, postId, new Date());
    }

    public void remove(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/postId 非法");
        }
        bookmarkMapper.deleteBookmark(userId, postId);
    }

    public boolean hasBookmarked(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            return false;
        }
        return bookmarkMapper.existsBookmark(userId, postId) > 0;
    }

    public List<DiscussPost> listBookmarkedPosts(int userId, int page, int size) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        return bookmarkMapper.selectBookmarkedPosts(userId, Pagination.safeOffset(p, s), s);
    }

    public List<PostSummaryView> listBookmarkedPostSummaries(int userId, int page, int size) {
        List<DiscussPost> posts = listBookmarkedPosts(userId, page, size);
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);
        return posts.stream()
                .map(post -> postSummaryAssembler.assemble(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .toList();
    }
}
