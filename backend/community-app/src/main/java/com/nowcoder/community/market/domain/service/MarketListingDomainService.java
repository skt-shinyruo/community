package com.nowcoder.community.market.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public final class MarketListingDomainService {

    public void validateCreateListing(UUID sellerUserId, String title, long unitPrice, int stockTotal) {
        validateListingBasics(sellerUserId, title, unitPrice);
        if (stockTotal <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing stockTotal must be positive");
        }
    }

    public void validateListingBasics(UUID sellerUserId, String title, Long unitPrice) {
        if (sellerUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "sellerUserId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing title must not be blank");
        }
        if (unitPrice == null || unitPrice <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "market listing unitPrice must be positive");
        }
    }
}
