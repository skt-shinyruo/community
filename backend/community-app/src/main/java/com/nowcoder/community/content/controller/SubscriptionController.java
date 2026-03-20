// 订阅 API：MVP 先支持订阅分类（category）以及查询订阅列表。
package com.nowcoder.community.content.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.content.service.SubscriptionService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PutMapping("/categories/{categoryId}/subscribe")
    public Result<Void> subscribeCategory(Authentication authentication, @PathVariable int categoryId) {
        int userId = CurrentUser.requireUserId(authentication);
        subscriptionService.subscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @DeleteMapping("/categories/{categoryId}/subscribe")
    public Result<Void> unsubscribeCategory(Authentication authentication, @PathVariable int categoryId) {
        int userId = CurrentUser.requireUserId(authentication);
        subscriptionService.unsubscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @GetMapping("/subscriptions/categories")
    public Result<List<Integer>> myCategories(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(subscriptionService.listSubscribedCategoryIds(userId));
    }
}
