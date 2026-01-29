package com.nowcoder.community.common.idempotency;

import java.time.Duration;

/**
 * 幂等存储抽象（SSOT）：
 * - 以同一份存储在多实例间共享幂等状态
 * - 通过 key -> (PROCESSING/SUCCEEDED + response) 实现“同 key 只产生一次副作用”
 */
public interface IdempotencyStore {

    /**
     * 尝试占用幂等 key（初始化为 PROCESSING）。
     *
     * @return true 表示占用成功（本次为 first-time）；false 表示 key 已存在（可能是并发/重复）
     */
    boolean tryAcquireProcessing(String key, Duration ttl);

    String get(String key);

    void save(String key, String value, Duration ttl);

    void delete(String key);
}

