package com.nowcoder.community.notice.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.notice.dto.MarkNoticeReadRequest;
import com.nowcoder.community.notice.dto.NoticeItemResponse;
import com.nowcoder.community.notice.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.notice.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public Result<List<NoticeItemResponse>> list(
            Authentication authentication,
            @RequestParam String topic,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(noticeService.listNoticeItems(userId, topic, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String topic) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(noticeService.unreadCount(userId, topic));
    }

    @GetMapping("/summary")
    public Result<List<NoticeTopicSummaryResponse>> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(noticeService.topicSummary(userId));
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkNoticeReadRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        noticeService.markRead(userId, request.getIds());
        return Result.ok();
    }
}
