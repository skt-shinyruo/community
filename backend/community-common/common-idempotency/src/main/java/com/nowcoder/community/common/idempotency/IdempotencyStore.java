package com.nowcoder.community.common.idempotency;

import java.time.Duration;
import java.util.UUID;

/**
 * 幂等存储抽象（SSOT）：
 * - 以同一份存储在多实例间共享幂等状态
 * - 通过 key -> (PROCESSING/SUCCESS/INDETERMINATE + response) 实现“同 key 只产生一次副作用”
 */
public interface IdempotencyStore {

    int MAX_REQUEST_HASH_LENGTH = 64;

    /**
     * 尝试占用幂等 key（初始化为 PROCESSING）。
     *
     * @return true 表示占用成功（本次为 first-time）；false 表示 key 已存在（可能是并发/重复）
     */
    boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl);

    Entry get(String operation, UUID userId, String key);

    /**
     * 将匹配 request hash 的 PROCESSING 记录转换为 SUCCESS。
     *
     * @return 仅当恰好一条记录完成转换时返回 true
     */
    boolean saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl);

    void extendProcessing(String operation, UUID userId, String key, Duration ttl);

    void delete(String operation, UUID userId, String key);

    enum Status {
        PROCESSING,
        SUCCESS,
        INDETERMINATE
    }

    /**
     * 幂等记录：
     * - PROCESSING：处理中（并发互斥/提示稍后重试）
     * - SUCCESS：已成功（直接复用响应 JSON）
     * - INDETERMINATE：业务事务结果未知（调用方应查询业务状态）
     */
    record Entry(Status status, String successJson, String requestHash) {

        public Entry {
            if (status == null) {
                throw new IllegalArgumentException("status is null");
            }
            requestHash = requireRequestHash(requestHash);
        }
    }

    static String requireRequestHash(String requestHash) {
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash is blank");
        }
        String normalized = requestHash.trim();
        if (normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("requestHash contains a line break");
        }
        if (normalized.length() > MAX_REQUEST_HASH_LENGTH) {
            throw new IllegalArgumentException("requestHash is too long");
        }
        return normalized;
    }
}
