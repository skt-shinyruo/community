// 收藏 API：收藏/取消收藏 + 我的收藏列表。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.dto.PostSummaryResponse;
import com.nowcoder.community.content.service.BookmarkApplicationService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkApplicationService bookmarkApplicationService;

    public BookmarkController(BookmarkApplicationService bookmarkApplicationService) {
        this.bookmarkApplicationService = bookmarkApplicationService;
    }

    @PutMapping("/posts/{postId}/bookmark")
    public Result<Void> bookmark(Authentication authentication, @PathVariable UUID postId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        bookmarkApplicationService.add(userId, postId);
        return Result.ok();
    }

    @DeleteMapping("/posts/{postId}/bookmark")
    public Result<Void> unbookmark(Authentication authentication, @PathVariable UUID postId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        bookmarkApplicationService.remove(userId, postId);
        return Result.ok();
    }

    @GetMapping("/bookmarks")
    public Result<List<PostSummaryResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(bookmarkApplicationService.listBookmarkedPostSummaryResponses(userId, p, s));
    }
}
