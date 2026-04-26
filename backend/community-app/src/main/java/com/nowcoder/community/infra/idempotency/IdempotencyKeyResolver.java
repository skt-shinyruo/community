package com.nowcoder.community.infra.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;

public final class IdempotencyKeyResolver {

    private IdempotencyKeyResolver() {
    }

    public static EffectiveIdempotencyKey resolve(String headerKey, String bodyRequestId) {
        String header = normalize(headerKey);
        String body = normalize(bodyRequestId);
        boolean hasHeader = header != null;
        boolean hasBody = body != null;

        if (hasHeader && hasBody) {
            if (!header.equals(body)) {
                throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "Idempotency-Key and requestId must match");
            }
            return new EffectiveIdempotencyKey(header, EffectiveIdempotencyKey.Source.HEADER_BODY_EQUAL);
        }
        if (hasHeader) {
            return new EffectiveIdempotencyKey(header, EffectiveIdempotencyKey.Source.HEADER);
        }
        if (hasBody) {
            return new EffectiveIdempotencyKey(body, EffectiveIdempotencyKey.Source.BODY_FALLBACK);
        }
        throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "Idempotency-Key is required");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
