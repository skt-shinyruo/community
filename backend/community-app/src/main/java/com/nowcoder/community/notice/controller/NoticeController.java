package com.nowcoder.community.notice.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.notice.application.NoticeApplicationService;
import com.nowcoder.community.notice.application.command.ListNoticeItemsCommand;
import com.nowcoder.community.notice.application.command.MarkNoticeReadCommand;
import com.nowcoder.community.notice.application.result.NoticeItemResult;
import com.nowcoder.community.notice.application.result.NoticeTopicSummaryResult;
import com.nowcoder.community.notice.controller.dto.MarkNoticeReadRequest;
import com.nowcoder.community.notice.controller.dto.NoticeItemResponse;
import com.nowcoder.community.notice.controller.dto.NoticeTopicSummaryResponse;
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

    private final NoticeApplicationService noticeApplicationService;

    public NoticeController(NoticeApplicationService noticeApplicationService) {
        this.noticeApplicationService = noticeApplicationService;
    }

    @GetMapping
    public Result<List<NoticeItemResponse>> list(
            Authentication authentication,
            @RequestParam String topic,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(noticeApplicationService.listNoticeItems(new ListNoticeItemsCommand(userId, topic, page, size))
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String topic) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(noticeApplicationService.unreadCount(userId, topic));
    }

    @GetMapping("/summary")
    public Result<List<NoticeTopicSummaryResponse>> summary(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(noticeApplicationService.topicSummary(userId).stream().map(this::toResponse).toList());
    }

    @PutMapping("/read")
    public Result<Void> markRead(Authentication authentication, @Valid @RequestBody MarkNoticeReadRequest request) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        noticeApplicationService.markRead(new MarkNoticeReadCommand(userId, request.getIds()));
        return Result.ok();
    }

    private NoticeItemResponse toResponse(NoticeItemResult result) {
        NoticeItemResponse response = new NoticeItemResponse();
        response.setId(result.id());
        response.setSenderUserId(result.senderUserId());
        response.setRecipientUserId(result.recipientUserId());
        response.setTopic(result.topic());
        response.setContent(result.content());
        response.setStatus(result.status());
        response.setCreateTime(result.createTime());
        return response;
    }

    private NoticeTopicSummaryResponse toResponse(NoticeTopicSummaryResult result) {
        NoticeTopicSummaryResponse response = new NoticeTopicSummaryResponse();
        response.setTopic(result.topic());
        response.setLatest(result.latest() == null ? null : toResponse(result.latest()));
        response.setNoticeCount(result.noticeCount());
        response.setUnreadCount(result.unreadCount());
        return response;
    }
}
