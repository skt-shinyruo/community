package com.nowcoder.community.wallet.domain.model;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;

import java.util.Date;
import java.util.UUID;

public class RechargeOrder {

    private UUID orderId;
    private String requestId;
    private UUID userId;
    private long amount;
    private String status;
    private String channel;
    private String channelOrderId;
    private String remark;
    private Date createTime;
    private Date updateTime;

    public static RechargeOrder create(UUID orderId, String requestId, UUID userId, long amount) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderId(orderId);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setStatus(RechargeOrderStatus.CREATED.code());
        return order;
    }

    public RechargeOrderStatus status() {
        return RechargeOrderStatus.fromCode(status);
    }

    public boolean isPaid() {
        return RechargeOrderStatus.PAID.equals(status());
    }

    public void assertReplayMatches(UUID userId, long amount) {
        if (!this.userId.equals(userId) || this.amount != amount) {
            throw new BusinessException(
                    WalletErrorCode.REQUEST_REPLAY_CONFLICT,
                    "requestId replay conflict: requestId=" + requestId
            );
        }
    }

    public RechargeOrderTransition pay() {
        if (!RechargeOrderStatus.CREATED.equals(status())) {
            throw new BusinessException(
                    WalletErrorCode.INVALID_REQUEST,
                    "recharge order status mismatch: orderId=" + orderId
            );
        }
        return new RechargeOrderTransition(
                orderId,
                userId,
                requestId,
                RechargeOrderStatus.CREATED,
                RechargeOrderStatus.PAID
        );
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getChannelOrderId() {
        return channelOrderId;
    }

    public void setChannelOrderId(String channelOrderId) {
        this.channelOrderId = channelOrderId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
