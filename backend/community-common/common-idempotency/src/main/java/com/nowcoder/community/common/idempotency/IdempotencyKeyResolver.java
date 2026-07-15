package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;

public final class IdempotencyKeyResolver {

    private IdempotencyKeyResolver() {
    }

    public static EffectiveIdempotencyKey resolve(String headerKey) {
        String header = normalize(headerKey);
        if (header == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "Idempotency-Key is required");
        }
        return new EffectiveIdempotencyKey(header);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
