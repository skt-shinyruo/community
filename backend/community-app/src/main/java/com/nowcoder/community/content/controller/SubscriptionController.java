// 订阅 API：MVP 先支持订阅分类（category）以及查询订阅列表。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.application.SubscriptionApplicationService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionApplicationService subscriptionApplicationService;

    public SubscriptionController(SubscriptionApplicationService subscriptionApplicationService) {
        this.subscriptionApplicationService = subscriptionApplicationService;
    }

    @PutMapping("/categories/{categoryId}/subscribe")
    public Result<Void> subscribeCategory(Authentication authentication, @PathVariable UUID categoryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        subscriptionApplicationService.subscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @DeleteMapping("/categories/{categoryId}/subscribe")
    public Result<Void> unsubscribeCategory(Authentication authentication, @PathVariable UUID categoryId) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        subscriptionApplicationService.unsubscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @GetMapping("/subscriptions/categories")
    public Result<List<UUID>> myCategories(Authentication authentication) {
        UUID userId = CurrentUser.requireUserUuid(authentication);
        return Result.ok(subscriptionApplicationService.listSubscribedCategoryIds(userId));
    }
}
