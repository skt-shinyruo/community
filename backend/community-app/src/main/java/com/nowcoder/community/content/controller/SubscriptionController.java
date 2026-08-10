// 订阅 API：查询订阅分类列表。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.SubscriptionQuery;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionQuery subscriptionQuery;

    public SubscriptionController(SubscriptionQuery subscriptionQuery) {
        this.subscriptionQuery = subscriptionQuery;
    }

    @GetMapping("/subscriptions/categories")
    public Result<List<UUID>> myCategories(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(subscriptionQuery.listSubscribedCategoryIds(userId));
    }
}
