package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public final class MarketOrderDomainService {

    public void validateCreateOrder(UUID buyerUserId, UUID sellerUserId, int quantity) {
        if (buyerUserId == null || sellerUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "market order participants must not be null");
        }
        if (Objects.equals(buyerUserId, sellerUserId)) {
            throw new BusinessException(INVALID_ARGUMENT, "buyer cannot purchase own listing");
        }
        if (quantity <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "quantity must be positive");
        }
    }

    public void validateBuyerAction(UUID actorUserId, UUID buyerUserId) {
        if (!Objects.equals(actorUserId, buyerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order buyer");
        }
    }

    public void validateSellerAction(UUID actorUserId, UUID sellerUserId) {
        if (!Objects.equals(actorUserId, sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market order seller");
        }
    }
}
