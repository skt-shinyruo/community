package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.Objects;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;

public final class MarketDisputeDomainService {

    public void validateBuyerCanOpen(UUID actorUserId, UUID buyerUserId) {
        if (!Objects.equals(actorUserId, buyerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market dispute buyer");
        }
    }

    public void validateSellerCanResolve(UUID actorUserId, UUID sellerUserId) {
        if (!Objects.equals(actorUserId, sellerUserId)) {
            throw new BusinessException(FORBIDDEN, "actor is not market dispute seller");
        }
    }
}
