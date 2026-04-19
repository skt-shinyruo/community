package com.nowcoder.community.auth.service;

import java.time.Duration;

public interface CaptchaStore {

    enum VerifyResult {
        MATCHED,
        MISMATCH,
        NOT_FOUND
    }

    void save(String owner, String code, Duration ttl);

    VerifyResult verifyAndConsume(String owner, String code);

    String get(String owner);

    void delete(String owner);

    /**
     * 累加该 captcha 的失败次数（用于失败阈值后作废）。
     * 返回累加后的失败次数；若入参非法则返回 0。
     */
    int incrementFailures(String owner, Duration ttl);
}
