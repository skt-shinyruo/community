// 收藏 API：收藏/取消收藏 + 我的收藏列表。
package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.api.dto.PostSummaryResponse;
import com.nowcoder.community.content.entity.Comment;
import com.nowcoder.community.content.entity.DiscussPost;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.content.service.CommentService;
import com.nowcoder.community.content.service.TagService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@RestController
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final CommentService commentService;
    private final TagService tagService;

    public BookmarkController(BookmarkService bookmarkService, CommentService commentService, TagService tagService) {
        this.bookmarkService = bookmarkService;
        this.commentService = commentService;
        this.tagService = tagService;
    }

    @PutMapping("/posts/{postId}/bookmark")
    public Result<Void> bookmark(Authentication authentication, @PathVariable int postId) {
        int userId = currentUserId(authentication);
        bookmarkService.add(userId, postId);
        return Result.ok();
    }

    @DeleteMapping("/posts/{postId}/bookmark")
    public Result<Void> unbookmark(Authentication authentication, @PathVariable int postId) {
        int userId = currentUserId(authentication);
        bookmarkService.remove(userId, postId);
        return Result.ok();
    }

    @GetMapping("/bookmarks")
    public Result<List<PostSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = currentUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));

        List<DiscussPost> posts = bookmarkService.listBookmarkedPosts(userId, p, s);
        List<Integer> postIds = posts.stream().map(DiscussPost::getId).toList();
        Map<Integer, Comment> lastActivities = commentService.getLatestPostActivitiesByPostIds(postIds);
        Map<Integer, List<String>> tagsByPostId = tagService.getTagsByPostIds(postIds);

        List<PostSummaryResponse> items = posts.stream()
                .map(post -> toSummary(post, lastActivities.get(post.getId()), tagsByPostId.get(post.getId())))
                .collect(Collectors.toList());
        return Result.ok(items);
    }

    private PostSummaryResponse toSummary(DiscussPost post, Comment lastActivity, List<String> tags) {
        PostSummaryResponse r = new PostSummaryResponse();
        r.setId(post.getId());
        r.setUserId(post.getUserId());
        r.setCategoryId(post.getCategoryId());
        r.setTags(tags == null ? List.of() : tags);
        r.setTitle(post.getTitle());
        r.setType(post.getType());
        r.setStatus(post.getStatus());
        r.setCreateTime(post.getCreateTime());
        r.setCommentCount(post.getCommentCount());
        r.setScore(post.getScore());

        if (lastActivity != null && lastActivity.getUserId() > 0 && lastActivity.getCreateTime() != null) {
            r.setLastReplyUserId(lastActivity.getUserId());
            r.setLastReplyTime(lastActivity.getCreateTime());
        }
        if (r.getLastReplyTime() != null) {
            if (r.getCreateTime() == null || r.getLastReplyTime().after(r.getCreateTime())) {
                r.setLastActivityTime(r.getLastReplyTime());
            } else {
                r.setLastActivityTime(r.getCreateTime());
            }
        } else {
            r.setLastActivityTime(r.getCreateTime());
        }
        return r;
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(INVALID_ARGUMENT, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (Exception e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}

