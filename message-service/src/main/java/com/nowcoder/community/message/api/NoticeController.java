package com.nowcoder.community.message.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.message.api.dto.MarkReadRequest;
import com.nowcoder.community.message.api.dto.NoticeTopicSummaryResponse;
import com.nowcoder.community.message.entity.Message;
import com.nowcoder.community.message.service.NoticeService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public Result<List<Message>> list(
            Authentication authentication,
            @RequestParam String topic,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 10 : Math.min(50, Math.max(1, size));
        return Result.ok(noticeService.listNotices(userId, topic, p, s));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(Authentication authentication, @RequestParam(required = false) String topic) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        return Result.ok(noticeService.unreadCount(userId, topic));
    }

    @GetMapping("/summary")
    public Result<List<NoticeTopicSummaryResponse>> summary(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        int userId = Integer.parseInt(jwt.getSubject());
        return Result.ok(noticeService.topicSummary(userId));
    }

    @PutMapping("/read")
    public Result<Void> markRead(@Valid @RequestBody MarkReadRequest request) {
        noticeService.markRead(request.getIds());
        return Result.ok();
    }
}
