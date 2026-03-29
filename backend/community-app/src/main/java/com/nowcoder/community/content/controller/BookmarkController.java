// 收藏 API：收藏/取消收藏 + 我的收藏列表。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.api.model.PostSummaryView;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.service.BookmarkService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PutMapping("/posts/{postId}/bookmark")
    public Result<Void> bookmark(Authentication authentication, @PathVariable int postId) {
        int userId = CurrentUser.requireUserId(authentication);
        bookmarkService.add(userId, postId);
        return Result.ok();
    }

    @DeleteMapping("/posts/{postId}/bookmark")
    public Result<Void> unbookmark(Authentication authentication, @PathVariable int postId) {
        int userId = CurrentUser.requireUserId(authentication);
        bookmarkService.remove(userId, postId);
        return Result.ok();
    }

    @GetMapping("/bookmarks")
    public Result<List<PostSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(bookmarkService.listBookmarkedPostSummaries(userId, p, s).stream()
                .map(BookmarkController::toPostSummaryResponse)
                .toList());
    }

    private static PostSummaryResponse toPostSummaryResponse(PostSummaryView view) {
        PostSummaryResponse response = new PostSummaryResponse();
        response.setId(view.id());
        response.setUserId(view.userId());
        response.setTitle(view.title());
        response.setType(view.type());
        response.setStatus(view.status());
        response.setCreateTime(view.createTime());
        response.setCommentCount(view.commentCount());
        response.setScore(view.score());
        response.setCategoryId(view.categoryId());
        response.setTags(view.tags());
        response.setLastReplyUserId(view.lastReplyUserId());
        response.setLastReplyTime(view.lastReplyTime());
        response.setLastActivityTime(view.lastActivityTime());
        response.setLastReplyPreview(view.lastReplyPreview());
        return response;
    }
}
