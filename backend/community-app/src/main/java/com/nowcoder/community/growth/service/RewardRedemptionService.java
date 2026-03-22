package com.nowcoder.community.growth.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.growth.entity.RewardItem;
import com.nowcoder.community.growth.entity.RewardOrder;
import com.nowcoder.community.growth.exception.GrowthErrorCode;
import com.nowcoder.community.growth.mapper.RewardItemMapper;
import com.nowcoder.community.growth.mapper.RewardOrderMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardRedemptionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_FULFILLED = "FULFILLED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";

    private final RewardItemMapper rewardItemMapper;
    private final RewardOrderMapper rewardOrderMapper;
    private final RewardAccountService rewardAccountService;

    public RewardRedemptionService(
            RewardItemMapper rewardItemMapper,
            RewardOrderMapper rewardOrderMapper,
            RewardAccountService rewardAccountService
    ) {
        this.rewardItemMapper = rewardItemMapper;
        this.rewardOrderMapper = rewardOrderMapper;
        this.rewardAccountService = rewardAccountService;
    }

    @Transactional
    public RewardOrder redeem(int userId, long itemId, String redeemRequestId) {
        RewardOrder existing = rewardOrderMapper.selectByUserIdAndRedeemRequestId(userId, redeemRequestId);
        if (existing != null) {
            verifyRequestTargetsSameItem(existing, itemId, redeemRequestId);
            return existing;
        }

        RewardItem item = rewardItemMapper.selectByIdForUpdate(itemId);
        if (item == null || !STATUS_ACTIVE.equals(item.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ITEM_UNAVAILABLE, "reward item unavailable: itemId=" + itemId);
        }
        RewardOrder existingAfterLock = rewardOrderMapper.selectByUserIdAndRedeemRequestIdForUpdate(userId, redeemRequestId);
        if (existingAfterLock != null) {
            verifyRequestTargetsSameItem(existingAfterLock, itemId, redeemRequestId);
            return existingAfterLock;
        }

        RewardOrder rewardOrder = new RewardOrder();
        rewardOrder.setRedeemRequestId(redeemRequestId);
        rewardOrder.setUserId(userId);
        rewardOrder.setItemId(itemId);
        rewardOrder.setStatus(resolveInitialStatus(item.getFulfillmentMode()));
        rewardOrder.setCostBalanceSnapshot(item.getCostBalance());
        rewardOrder.setFulfillmentModeSnapshot(item.getFulfillmentMode());
        rewardOrder.setItemNameSnapshot(item.getItemName());
        rewardOrder.setItemDescSnapshot(item.getItemDesc());

        try {
            rewardOrderMapper.insert(rewardOrder);
        } catch (DataIntegrityViolationException e) {
            RewardOrder duplicated = rewardOrderMapper.selectByUserIdAndRedeemRequestIdForUpdate(userId, redeemRequestId);
            if (duplicated != null) {
                verifyRequestTargetsSameItem(duplicated, itemId, redeemRequestId);
                return duplicated;
            }
            throw e;
        }

        if (rewardItemMapper.reserveStockForRedemption(itemId, userId) != 1) {
            if (item.getPerUserLimit() > 0
                    && rewardOrderMapper.countActiveUserOrdersForItem(userId, itemId) > item.getPerUserLimit()) {
                throw new BusinessException(GrowthErrorCode.REWARD_ITEM_LIMIT_EXCEEDED, "reward item limit exceeded: itemId=" + itemId);
            }
            throw new BusinessException(GrowthErrorCode.REWARD_ITEM_SOLD_OUT, "reward item sold out: itemId=" + itemId);
        }

        if (MODE_MANUAL.equals(item.getFulfillmentMode())) {
            rewardAccountService.moveAvailableToFrozen(
                    userId,
                    "reward-order:" + rewardOrder.getId() + ":freeze",
                    "RewardRedeemedPending",
                    item.getCostBalance(),
                    "growth",
                    "reward-freeze"
            );
        } else {
            rewardAccountService.applyAvailableDelta(
                    userId,
                    "reward-order:" + rewardOrder.getId() + ":debit",
                    "RewardRedeemed",
                    -item.getCostBalance(),
                    "growth",
                    "reward-redeem"
            );
        }

        return rewardOrderMapper.selectById(rewardOrder.getId());
    }

    private void verifyRequestTargetsSameItem(RewardOrder existing, long itemId, String redeemRequestId) {
        if (existing.getItemId() != itemId) {
            throw new BusinessException(
                    GrowthErrorCode.INVALID_REQUEST,
                    "redeem request already used for another item: requestId=" + redeemRequestId
            );
        }
    }

    private String resolveInitialStatus(String fulfillmentMode) {
        return MODE_AUTO.equals(fulfillmentMode) ? STATUS_FULFILLED : STATUS_PENDING;
    }

    @Transactional
    public RewardOrder cancelPendingOrder(long orderId) {
        RewardOrder order = requireOrder(orderId);
        if (STATUS_CANCELLED.equals(order.getStatus())) {
            return order;
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ORDER_STATE_CONFLICT, "reward order is not pending: orderId=" + orderId);
        }
        if (rewardOrderMapper.updateStatus(orderId, STATUS_PENDING, STATUS_CANCELLED) != 1) {
            return requireOrder(orderId);
        }
        rewardAccountService.moveFrozenToAvailable(
                order.getUserId(),
                "reward-order:" + orderId + ":cancel",
                "RewardOrderCancelled",
                order.getCostBalanceSnapshot(),
                "growth",
                "reward-cancel"
        );
        return requireOrder(orderId);
    }

    @Transactional
    public RewardOrder refundFulfilledOrder(long orderId) {
        RewardOrder order = requireOrder(orderId);
        if (STATUS_REFUNDED.equals(order.getStatus())) {
            return order;
        }
        if (!STATUS_FULFILLED.equals(order.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ORDER_STATE_CONFLICT, "reward order is not fulfilled: orderId=" + orderId);
        }
        if (rewardOrderMapper.updateStatus(orderId, STATUS_FULFILLED, STATUS_REFUNDED) != 1) {
            return requireOrder(orderId);
        }
        rewardAccountService.applyAvailableDelta(
                order.getUserId(),
                "reward-order:" + orderId + ":refund",
                "RewardOrderRefunded",
                order.getCostBalanceSnapshot(),
                "growth",
                "reward-refund"
        );
        return requireOrder(orderId);
    }

    @Transactional
    public RewardOrder fulfillPendingOrder(long orderId) {
        RewardOrder order = requireOrder(orderId);
        if (STATUS_FULFILLED.equals(order.getStatus())) {
            return order;
        }
        if (!STATUS_PENDING.equals(order.getStatus())) {
            throw new BusinessException(GrowthErrorCode.REWARD_ORDER_STATE_CONFLICT, "reward order is not pending: orderId=" + orderId);
        }
        if (rewardOrderMapper.updateStatus(orderId, STATUS_PENDING, STATUS_FULFILLED) != 1) {
            return requireOrder(orderId);
        }
        rewardAccountService.deductFrozenBalance(
                order.getUserId(),
                "reward-order:" + orderId + ":fulfill",
                "RewardOrderFulfilled",
                order.getCostBalanceSnapshot(),
                "growth",
                "reward-fulfill"
        );
        return requireOrder(orderId);
    }

    private RewardOrder requireOrder(long orderId) {
        RewardOrder order = rewardOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(GrowthErrorCode.REWARD_ORDER_NOT_FOUND, "reward order not found: orderId=" + orderId);
        }
        return order;
    }
}
