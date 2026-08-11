package com.nowcoder.community.wallet.application;

import org.springframework.transaction.annotation.Transactional;

public class WalletDeadlockRetryTestTarget {

    private final int failuresBeforeSuccess;
    private final RuntimeException failure;
    private int attempts;

    public WalletDeadlockRetryTestTarget(int failuresBeforeSuccess, RuntimeException failure) {
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.failure = failure;
    }

    @Transactional
    public String execute() {
        attempts++;
        if (attempts <= failuresBeforeSuccess) {
            throw failure;
        }
        return "ok";
    }

    public int attempts() {
        return attempts;
    }
}
