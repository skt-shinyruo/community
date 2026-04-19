package com.nowcoder.community.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
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

    private final ObjectMapper objectMapper;
    private final IdempotencyStore store;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final IdempotencyProperties properties;

    public IdempotencyGuard(
            ObjectMapper objectMapper,
            IdempotencyStore store,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            IdempotencyProperties properties
    ) {
        this.objectMapper = objectMapper;
        if (store == null) {
            throw new IllegalStateException("idempotency store is null");
        }
        this.store = store;
        this.meterRegistryProvider = meterRegistryProvider;
        this.properties = properties == null ? new IdempotencyProperties() : properties;
    }

    public <T> T executeRequired(String operation, int userId, String idempotencyKey, Class<T> type, Supplier<T> supplier) {
        return execute(operation, userId, idempotencyKey, type, supplier, true);
    }

    public <T> T execute(String operation, int userId, String idempotencyKey, Class<T> type, Supplier<T> supplier, boolean failClosedOnStoreError) {
        if (userId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "userId 非法");
        }
        if (!StringUtils.hasText(operation)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "operation 未配置");
        }
        if (!StringUtils.hasText(idempotencyKey)) {
            record(operation, "missing_key");
            throw new BusinessException(
                    CommonErrorCode.INVALID_ARGUMENT,
                    HEADER_IDEMPOTENCY_KEY + " 不能为空（写请求必须携带幂等键；可参考 docs/SECURITY.md）"
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

        boolean acquired;
        try {
            acquired = store.tryAcquireProcessing(op, userId, key, processingTtl);
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
                store.saveSuccess(op, userId, key, json, successTtl);
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
        if (existing.status() == IdempotencyStore.Status.SUCCESS) {
            record(operation, "duplicate");
            return fromJson(existing.successJson(), type);
        }
        if (existing.status() == IdempotencyStore.Status.PROCESSING) {
            record(operation, "concurrent_conflict");
            throw new BusinessException(new SimpleErrorCode(409, "请求处理中，请稍后重试", 409));
        }

        record(operation, "unknown_state");
        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等状态不合法");
    }

    private String normalizeKey(String key) {
        return key.trim();
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper == null ? String.valueOf(value) : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
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
            if (objectMapper == null) {
                throw new IllegalStateException("objectMapper is null");
            }
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("deserialize idempotency response failed", e);
        }
    }

    private void safeDelete(String operation, int userId, String key) {
        try {
            store.delete(operation, userId, key);
        } catch (RuntimeException ignored) {
        }
    }

    private void safeExtendProcessing(String operation, int userId, String key, Duration ttl) {
        try {
            store.extendProcessing(operation, userId, key, ttl);
        } catch (RuntimeException ignored) {
        }
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
        return new BusinessException(new SimpleErrorCode(409, "请求结果确认中，请稍后重试", 409));
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
