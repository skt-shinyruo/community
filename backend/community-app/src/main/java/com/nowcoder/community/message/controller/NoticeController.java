package com.nowcoder.community.message.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.dto.LetterItemResponse;
import com.nowcoder.community.message.dto.MarkReadRequest;
import com.nowcoder.community.message.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.message.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public Result<List<LetterItemResponse>> list(
            Authentication authentication,
            @RequestParam String topic,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(noticeService.listNoticeItems(userId, topic, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String topic) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(noticeService.unreadCount(userId, topic));
    }

    @GetMapping("/summary")
    public Result<List<NoticeTopicSummaryResponse>> summary(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(noticeService.topicSummary(userId));
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkReadRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        noticeService.markRead(userId, request.getIds());
        return Result.ok();
    }
}
