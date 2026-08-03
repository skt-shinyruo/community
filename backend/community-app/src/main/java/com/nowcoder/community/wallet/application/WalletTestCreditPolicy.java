package com.nowcoder.community.wallet.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.wallet.exception.WalletErrorCode;
import org.springframework.stereotype.Component;

@Component
public class WalletTestCreditPolicy {

    private final WalletTestCreditProperties properties;

    public WalletTestCreditPolicy(WalletTestCreditProperties properties) {
        this.properties = properties;
    }

    public void assertGrantAllowed(long amount) {
        if (!properties.isGrantAvailable()) {
            throw new BusinessException(WalletErrorCode.TEST_CREDITS_DISABLED);
        }
        if (amount > properties.getMaxGrantPerRequest()) {
            throw new BusinessException(
                    WalletErrorCode.TEST_CREDIT_LIMIT_EXCEEDED,
                    "单次最多领取 " + properties.getMaxGrantPerRequest() + " 测试积分"
            );
        }
    }

    public void assertDiscardAllowed(long amount) {
        if (!properties.isDiscardAvailable()) {
            throw new BusinessException(WalletErrorCode.TEST_CREDITS_DISABLED);
        }
        if (amount > properties.getMaxDiscardPerRequest()) {
            throw new BusinessException(
                    WalletErrorCode.TEST_CREDIT_LIMIT_EXCEEDED,
                    "单次最多销毁 " + properties.getMaxDiscardPerRequest() + " 测试积分"
            );
        }
    }

    public WalletTestCreditProperties properties() {
        return properties;
    }
}
