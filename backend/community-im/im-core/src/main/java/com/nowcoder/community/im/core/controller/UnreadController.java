package com.nowcoder.community.im.core.controller;

import com.nowcoder.community.im.core.application.UnreadApplicationService;
import com.nowcoder.community.im.core.application.result.UnreadSummaryResult;
import com.nowcoder.community.common.security.jwt.JwtSubjects;
import com.nowcoder.community.common.web.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/im/unread")
public class UnreadController {

    private final UnreadApplicationService unreadApplicationService;

    public UnreadController(UnreadApplicationService unreadApplicationService) {
        this.unreadApplicationService = unreadApplicationService;
    }

    @GetMapping("/summary")
    public Result<UnreadSummaryResult> summary(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "limit", required = false, defaultValue = "500") int limit
    ) {
        UUID me = JwtSubjects.userUuidOrThrow(jwt);
        return Result.ok(unreadApplicationService.summary(me, limit));
    }
}
