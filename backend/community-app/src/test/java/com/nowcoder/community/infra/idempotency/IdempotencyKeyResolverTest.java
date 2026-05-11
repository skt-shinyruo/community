package com.nowcoder.community.infra.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyResolverTest {

    @Test
    void resolveShouldRequireHeader() {
        assertThatThrownBy(() -> IdempotencyKeyResolver.resolve(null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(INVALID_ARGUMENT);
    }

    @Test
    void resolveShouldUseTrimmedHeader() {
        EffectiveIdempotencyKey key = IdempotencyKeyResolver.resolve(" request-1 ");

        assertThat(key.value()).isEqualTo("request-1");
    }
}
