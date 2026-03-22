package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.dto.AdminGrowthMetricsResponse;
import com.nowcoder.community.growth.dto.AdminRewardItemUpsertRequest;
import com.nowcoder.community.growth.dto.AdminRewardOrderActionRequest;
import com.nowcoder.community.growth.entity.AdminRewardOrderAction;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.AdminRewardOrderActionMapper;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminRewardOpsService {

    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final RewardItemMapper rewardItemMapper;
    private final RewardOrderMapper rewardOrderMapper;
    private final AdminRewardOrderActionMapper adminRewardOrderActionMapper;
    private final RewardRedemptionService rewardRedemptionService;

    public AdminRewardOpsService(
            RewardItemMapper rewardItemMapper,
            RewardOrderMapper rewardOrderMapper,
            AdminRewardOrderActionMapper adminRewardOrderActionMapper,
            RewardRedemptionService rewardRedemptionService
    ) {
        this.rewardItemMapper = rewardItemMapper;
        this.rewardOrderMapper = rewardOrderMapper;
        this.adminRewardOrderActionMapper = adminRewardOrderActionMapper;
        this.rewardRedemptionService = rewardRedemptionService;
    }

    public List<RewardItem> listItems() {
        return rewardItemMapper.selectAllOrdered();
    }

    public RewardItem upsertItem(AdminRewardItemUpsertRequest request) {
        if (request == null || !StringUtils.hasText(request.getItemName())) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "itemName required");
        }
        if (request.getCostBalance() < 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "costBalance must be >= 0");
        }
        if (request.getStock() < 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "stock must be >= 0");
        }
        if (request.getPerUserLimit() < 0) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "perUserLimit must be >= 0");
        }
        String fulfillmentMode = request.getFulfillmentMode() == null ? "" : request.getFulfillmentMode().trim();
        if (!MODE_AUTO.equals(fulfillmentMode) && !MODE_MANUAL.equals(fulfillmentMode)) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "fulfillmentMode must be AUTO or MANUAL");
        }
        String status = request.getStatus() == null ? "" : request.getStatus().trim();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "status must be ACTIVE or INACTIVE");
        }
        RewardItem item = new RewardItem();
        item.setId(request.getItemId() == null ? 0 : request.getItemId());
        item.setItemName(request.getItemName().trim());
        item.setItemDesc(request.getItemDesc());
        item.setCostBalance(request.getCostBalance());
        item.setStock(request.getStock());
        item.setPerUserLimit(request.getPerUserLimit());
        item.setFulfillmentMode(fulfillmentMode);
        item.setStatus(status);

        if (item.getId() > 0) {
            if (rewardItemMapper.update(item) != 1) {
                throw new BusinessException(GrowthErrorCode.REWARD_ITEM_UNAVAILABLE, "reward item unavailable: itemId=" + item.getId());
            }
            return rewardItemMapper.selectById(item.getId());
        }

        rewardItemMapper.insert(item);
        return rewardItemMapper.selectById(item.getId());
    }

    public List<RewardOrder> listOrders() {
        return rewardOrderMapper.selectAll();
    }

    @Transactional
    public RewardOrder processOrder(int actorUserId, AdminRewardOrderActionRequest request) {
        if (request == null || !request.isConfirm()) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "confirm=true required");
        }
        String note = request.getNote() == null ? "" : request.getNote().trim();
        if (!StringUtils.hasText(note)) {
            throw new BusinessException(GrowthErrorCode.INVALID_REQUEST, "note required");
        }
        String actionCode = String.valueOf(request.getAction());
        if (!"FULFILL".equals(actionCode) && !"CANCEL".equals(actionCode) && !"REFUND".equals(actionCode)) {
            throw new BusinessException(GrowthErrorCode.INVALID_ADMIN_REWARD_ACTION, "unsupported admin reward action");
        }
        RewardOrder before = rewardOrderMapper.selectByIdForUpdate(request.getOrderId());
        if (before == null) {
            throw new BusinessException(GrowthErrorCode.REWARD_ORDER_NOT_FOUND, "reward order not found: orderId=" + request.getOrderId());
        }
        RewardOrder after = switch (actionCode) {
            case "FULFILL" -> rewardRedemptionService.fulfillPendingOrder(request.getOrderId());
            case "CANCEL" -> rewardRedemptionService.cancelPendingOrder(request.getOrderId());
            case "REFUND" -> rewardRedemptionService.refundFulfilledOrder(request.getOrderId());
            default -> throw new IllegalStateException("validated unsupported admin reward action");
        };
        if (!before.getStatus().equals(after.getStatus())) {
            AdminRewardOrderAction action = new AdminRewardOrderAction();
            action.setOrderId(after.getId());
            action.setActorUserId(actorUserId);
            action.setAction(actionCode);
            action.setFromStatus(before.getStatus());
            action.setToStatus(after.getStatus());
            action.setNote(note);
            adminRewardOrderActionMapper.insert(action);
        }
        return after;
    }

    public AdminGrowthMetricsResponse metrics() {
        AdminGrowthMetricsResponse response = new AdminGrowthMetricsResponse();
        response.setActiveItemCount(rewardItemMapper.countActiveItems());
        response.setPendingOrderCount(rewardOrderMapper.countByStatus("PENDING"));
        response.setRefundedOrderCount(rewardOrderMapper.countByStatus("REFUNDED"));
        return response;
    }
}
