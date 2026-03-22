package com.nowcoder.community.growth.exception;

import com.nowcoder.community.common.exception.ErrorCode;

public enum GrowthErrorCode implements ErrorCode {

    INVALID_REQUEST(16001, "成长中心请求参数错误", 400),
    TARGET_USER_NOT_FOUND(16002, "目标用户不存在", 404),
    REWARD_ITEM_UNAVAILABLE(16003, "奖品不可兑换", 404),
    REWARD_ITEM_SOLD_OUT(16004, "奖品已兑完", 409),
    REWARD_ITEM_LIMIT_EXCEEDED(16005, "已达到兑换上限", 409),
    REWARD_BALANCE_INSUFFICIENT(16006, "奖励余额不足", 409),
    REWARD_FROZEN_BALANCE_INSUFFICIENT(16007, "冻结奖励余额不足", 409),
    REWARD_ORDER_NOT_FOUND(16008, "兑换订单不存在", 404),
    REWARD_ORDER_STATE_CONFLICT(16009, "兑换订单状态冲突", 409),
    INVALID_ADMIN_REWARD_ACTION(16010, "无效的后台奖励操作", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    GrowthErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
