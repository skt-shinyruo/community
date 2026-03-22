package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.RedeemRewardRequest;
import com.nowcoder.community.growth.dto.RewardItemResponse;
import com.nowcoder.community.growth.dto.RewardOrderResponse;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.entity.RewardOrder;
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
        return Result.ok(rewardCatalogService.listItemsForUser(userId).stream().map(this::toItemResponse).toList());
    }

    @GetMapping("/items/{itemId}")
    public Result<RewardItemResponse> item(Authentication authentication, @PathVariable long itemId) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(toItemResponse(rewardCatalogService.getItemForUser(userId, itemId)));
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
        RewardOrder order = rewardRedemptionService.redeem(userId, request.getItemId(), requestId);
        return Result.ok(toOrderResponse(order));
    }

    @GetMapping("/orders")
    public Result<List<RewardOrderResponse>> orders(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        return Result.ok(rewardOrderQueryService.listOrdersForUser(userId).stream().map(this::toOrderResponse).toList());
    }

    private RewardItemResponse toItemResponse(RewardItem item) {
        RewardItemResponse response = new RewardItemResponse();
        response.setId(item.getId());
        response.setItemName(item.getItemName());
        response.setItemDesc(item.getItemDesc());
        response.setCostBalance(item.getCostBalance());
        response.setStock(item.getStock());
        response.setPerUserLimit(item.getPerUserLimit());
        response.setFulfillmentMode(item.getFulfillmentMode());
        response.setStatus(item.getStatus());
        return response;
    }

    private RewardOrderResponse toOrderResponse(RewardOrder order) {
        RewardOrderResponse response = new RewardOrderResponse();
        response.setId(order.getId());
        response.setItemId(order.getItemId());
        response.setStatus(order.getStatus());
        response.setCostBalanceSnapshot(order.getCostBalanceSnapshot());
        response.setFulfillmentModeSnapshot(order.getFulfillmentModeSnapshot());
        response.setItemNameSnapshot(order.getItemNameSnapshot());
        response.setItemDescSnapshot(order.getItemDescSnapshot());
        return response;
    }
}
