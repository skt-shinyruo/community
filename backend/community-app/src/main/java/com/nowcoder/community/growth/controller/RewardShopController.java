package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.RedeemRewardRequest;
import com.nowcoder.community.growth.dto.RewardItemResponse;
import com.nowcoder.community.growth.dto.RewardOrderResponse;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.service.RewardCatalogService;
import com.nowcoder.community.growth.service.RewardOrderQueryService;
import com.nowcoder.community.growth.service.RewardRedemptionService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/growth/shop")
public class RewardShopController {

    private final RewardCatalogService rewardCatalogService;
    private final RewardOrderQueryService rewardOrderQueryService;
    private final RewardRedemptionService rewardRedemptionService;

    public RewardShopController(
            RewardCatalogService rewardCatalogService,
            RewardOrderQueryService rewardOrderQueryService,
            RewardRedemptionService rewardRedemptionService
    ) {
        this.rewardCatalogService = rewardCatalogService;
        this.rewardOrderQueryService = rewardOrderQueryService;
        this.rewardRedemptionService = rewardRedemptionService;
    }

    @GetMapping("/items")
    public Result<List<RewardItemResponse>> items(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(rewardCatalogService.listItemResponsesForUser(userId));
    }

    @GetMapping("/items/{itemId}")
    public Result<RewardItemResponse> item(Authentication authentication, @PathVariable long itemId) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(rewardCatalogService.getItemResponseForUser(userId, itemId));
    }

    @PostMapping("/redeem")
    public Result<RewardOrderResponse> redeem(Authentication authentication, @RequestBody RedeemRewardRequest request) {
        int userId = CurrentUser.requireUserId(authentication);
        if (request == null || request.getItemId() == null || request.getItemId() <= 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "reward item id required");
        }
        String requestId = request.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = "reward-redeem:" + UUID.randomUUID();
        }
        return Result.ok(rewardRedemptionService.redeemResponse(userId, request.getItemId(), requestId));
    }

    @GetMapping("/orders")
    public Result<List<RewardOrderResponse>> orders(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(rewardOrderQueryService.listOrderResponsesForUser(userId));
    }
}
