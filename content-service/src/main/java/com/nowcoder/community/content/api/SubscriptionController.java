// 订阅 API：MVP 先支持订阅分类（category）以及查询订阅列表。
package com.nowcoder.community.content.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.content.service.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.UNAUTHORIZED;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PutMapping("/categories/{categoryId}/subscribe")
    public Result<Void> subscribeCategory(Authentication authentication, @PathVariable int categoryId) {
        int userId = currentUserId(authentication);
        subscriptionService.subscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @DeleteMapping("/categories/{categoryId}/subscribe")
    public Result<Void> unsubscribeCategory(Authentication authentication, @PathVariable int categoryId) {
        int userId = currentUserId(authentication);
        subscriptionService.unsubscribeCategory(userId, categoryId);
        return Result.ok();
    }

    @GetMapping("/subscriptions/categories")
    public Result<List<Integer>> myCategories(Authentication authentication) {
        int userId = currentUserId(authentication);
        return Result.ok(subscriptionService.listSubscribedCategoryIds(userId));
    }

    private int currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String sub = jwt.getSubject();
        try {
            return Integer.parseInt(sub);
        } catch (Exception e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }
}
