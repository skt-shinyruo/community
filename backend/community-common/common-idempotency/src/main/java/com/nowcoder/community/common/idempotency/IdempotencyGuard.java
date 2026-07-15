package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * HTTP 写接口幂等保护（Idempotency-Key）：
 * - 同一 user + 同一 operation + 同一 key：只允许一次副作用
 * - 并发同 key：返回 409（可重试）
 * - 已成功：直接返回缓存响应（避免重复写入与重复副作用）
 * - 幂等存储不可用：按 fail-closed 返回 503（仅对“必须幂等”的入口）
 */
public class IdempotencyGuard {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String METRIC = "http_idempotency_total";

    private final JsonCodec jsonCodec;
    private final IdempotencyStore store;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final IdempotencyProperties properties;

    public IdempotencyGuard(
            JsonCodec jsonCodec,
            IdempotencyStore store,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            IdempotencyProperties properties
    ) {
        this.jsonCodec = jsonCodec;
        if (store == null) {
            throw new IllegalStateException("idempotency store is null");
        }
        this.store = store;
        this.meterRegistryProvider = meterRegistryProvider;
        this.properties = properties == null ? new IdempotencyProperties() : properties;
    }

    public <T> T executeRequired(String operation,
                                 UUID userId,
                                 String idempotencyKey,
                                 String requestHash,
                                 ErrorCode replayConflictCode,
                                 Class<T> type,
                                 Supplier<T> supplier) {
        return execute(operation, userId, idempotencyKey, requestHash, replayConflictCode, type, supplier, true);
    }

    private <T> T execute(String operation,
                          UUID userId,
                          String idempotencyKey,
                          String requestHash,
                          ErrorCode replayConflictCode,
                          Class<T> type,
                          Supplier<T> supplier,
                          boolean failClosedOnStoreError) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(operation)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "operation 未配置");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            record(operation, "missing_key");
            throw new BusinessException(
                    CommonErrorCode.INVALID_ARGUMENT,
                    HEADER_IDEMPOTENCY_KEY + " 不能为空（写请求必须携带幂等键；可参考 docs/handbook/reliability.md）"
            );
        }
        String key = normalizeKey(idempotencyKey);
        if (key.length() > 128) {
            record(operation, "invalid_key");
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, HEADER_IDEMPOTENCY_KEY + " 过长");
        }
        if (supplier == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "supplier 不能为空");
        }

        Duration processingTtl = safeDuration(properties == null ? null : properties.getProcessingTtl(), Duration.ofSeconds(30));
        Duration successTtl = safeDuration(properties == null ? null : properties.getSuccessTtl(), Duration.ofHours(24));
        String op = operation.trim().toLowerCase(Locale.ROOT);
        String hash = normalizeRequestHash(requestHash);

        boolean acquired;
        try {
            acquired = store.tryAcquireProcessing(op, userId, key, hash, processingTtl);
        } catch (RuntimeException e) {
            record(operation, "store_error");
            if (failClosedOnStoreError) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等存储不可用");
            }
            return supplier.get();
        }

        if (acquired) {
            record(operation, "first_time");
            final T result;
            try {
                result = supplier.get();
            } catch (RuntimeException e) {
                // 失败允许重试：删除占用 key，避免永久卡死在 PROCESSING
                safeDelete(op, userId, key);
                record(operation, "failed");
                throw e;
            }

            String json;
            try {
                json = toJson(result);
            } catch (RuntimeException e) {
                record(operation, "serialize_error");
                json = "null";
            }
            try {
                saveSuccess(op, userId, key, hash, json, successTtl);
            } catch (RuntimeException e) {
                record(operation, "store_error");
                safeExtendProcessing(op, userId, key, successTtl);
                throw pendingConfirmation();
            }
            record(operation, "succeeded");
            return result;
        }

        IdempotencyStore.Entry existing;
        try {
            existing = store.get(op, userId, key);
        } catch (RuntimeException e) {
            record(operation, "store_error");
            if (failClosedOnStoreError) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等存储不可用");
            }
            return supplier.get();
        }
        if (existing == null) {
            // 极端情况：刚判断为“已存在”，但随后读不到（可能已过期/被清理）
            record(operation, "race_miss");
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等状态读取失败");
        }
        if (!hash.equals(existing.requestHash())) {
            record(operation, "replay_conflict");
            throw replayConflict(replayConflictCode);
        }
        if (existing.status() == IdempotencyStore.Status.SUCCESS) {
            record(operation, "duplicate");
            return fromJson(existing.successJson(), type);
        }
        if (existing.status() == IdempotencyStore.Status.PROCESSING) {
            record(operation, "concurrent_conflict");
            throw new BusinessException(new SimpleErrorCode(409, "请求处理中，请稍后重试", ErrorKind.CONFLICT));
        }

        record(operation, "unknown_state");
        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等状态不合法");
    }

    private String normalizeKey(String key) {
        return key.trim();
    }

    private String normalizeRequestHash(String requestHash) {
        if (!StringUtils.hasText(requestHash)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "requestHash 不能为空");
        }
        String hash = requestHash.trim();
        if (hash.indexOf('\r') >= 0 || hash.indexOf('\n') >= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "requestHash 格式非法");
        }
        if (hash.length() > IdempotencyStore.MAX_REQUEST_HASH_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "requestHash 过长");
        }
        return hash;
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return jsonCodec == null ? String.valueOf(value) : jsonCodec.toJson(value);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("serialize idempotency response failed", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (type == null || type == Void.class) {
            return null;
        }
        if (!StringUtils.hasText(json) || "null".equals(json)) {
            return null;
        }
        try {
            if (jsonCodec == null) {
                throw new IllegalStateException("jsonCodec is null");
            }
            return jsonCodec.fromJson(json, type);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("deserialize idempotency response failed", e);
        }
    }

    private void safeDelete(String operation, UUID userId, String key) {
        try {
            store.delete(operation, userId, key);
        } catch (RuntimeException ignored) {
        }
    }

    private void safeExtendProcessing(String operation, UUID userId, String key, Duration ttl) {
        try {
            store.extendProcessing(operation, userId, key, ttl);
        } catch (RuntimeException ignored) {
        }
    }

    private void saveSuccess(String operation,
                             UUID userId,
                             String key,
                             String requestHash,
                             String successJson,
                             Duration successTtl) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            doSaveSuccess(operation, userId, key, requestHash, successJson, successTtl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doSaveSuccess(operation, userId, key, requestHash, successJson, successTtl);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    safeDelete(operation, userId, key);
                }
            }
        });
    }

    private void doSaveSuccess(String operation,
                               UUID userId,
                               String key,
                               String requestHash,
                               String successJson,
                               Duration successTtl) {
        store.saveSuccess(operation, userId, key, requestHash, successJson, successTtl);
    }

    private Duration safeDuration(Duration v, Duration fallback) {
        if (v == null) {
            return fallback;
        }
        if (v.isNegative() || v.isZero()) {
            return fallback;
        }
        return v;
    }

    private BusinessException pendingConfirmation() {
        return new BusinessException(new SimpleErrorCode(409, "请求结果确认中，请稍后重试", ErrorKind.CONFLICT));
    }

    private BusinessException replayConflict(ErrorCode replayConflictCode) {
        ErrorCode code = replayConflictCode == null
                ? new SimpleErrorCode(409, "请求参数与已有幂等请求不一致", ErrorKind.CONFLICT)
                : replayConflictCode;
        return new BusinessException(code);
    }

    private void record(String operation, String outcome) {
        MeterRegistry meterRegistry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            return;
        }
        String op = StringUtils.hasText(operation) ? operation.trim().toLowerCase(Locale.ROOT) : "unknown";
        String oc = StringUtils.hasText(outcome) ? outcome.trim().toLowerCase(Locale.ROOT) : "unknown";
        meterRegistry.counter(METRIC, Tags.of("op", op, "outcome", oc)).increment();
    }
}
