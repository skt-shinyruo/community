package com.nowcoder.community.auth.domain.repository;

import java.time.Duration;

public interface CaptchaRepository {
    void save(String owner, String code, Duration ttl);

    /**
     * Atomically verifies the code, records a mismatch, and invalidates the
     * challenge once the failure budget is exhausted.
     */
    boolean verifyAndConsume(String owner, String code, int maxFailures, Duration failureTtl);

    /**
     * 累加该 captcha 的失败次数（用于失败阈值后作废）。
     * 返回累加后的失败次数；若入参非法则返回 0。
     */
    int incrementFailures(String owner, Duration ttl);
}
