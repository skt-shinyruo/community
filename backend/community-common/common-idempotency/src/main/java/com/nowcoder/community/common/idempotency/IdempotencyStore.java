package com.nowcoder.community.common.idempotency;

import java.time.Duration;
import java.util.UUID;

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
    boolean tryAcquireProcessing(String operation, UUID userId, String key, Duration ttl);

    default boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl) {
        return tryAcquireProcessing(operation, userId, key, ttl);
    }

    Entry get(String operation, UUID userId, String key);

    void saveSuccess(String operation, UUID userId, String key, String successJson, Duration ttl);

    default void saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl) {
        saveSuccess(operation, userId, key, successJson, ttl);
    }

    void extendProcessing(String operation, UUID userId, String key, Duration ttl);

    void delete(String operation, UUID userId, String key);

    enum Status {
        PROCESSING,
        SUCCESS
    }

    /**
     * 幂等记录：
     * - PROCESSING：处理中（并发互斥/提示稍后重试）
     * - SUCCESS：已成功（直接复用响应 JSON）
     */
    record Entry(Status status, String successJson, String requestHash) {

        public Entry(Status status, String successJson) {
            this(status, successJson, null);
        }
    }
}
