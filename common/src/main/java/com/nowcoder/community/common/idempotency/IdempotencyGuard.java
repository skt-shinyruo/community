package com.nowcoder.community.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.SimpleErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
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
@Component
public class IdempotencyGuard {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private static final String KEY_PREFIX = "idem:";
    private static final String VALUE_PROCESSING = "P";
    private static final String VALUE_SUCCESS_PREFIX = "S\n";

    private static final String METRIC = "http_idempotency_total";

    private final ObjectMapper objectMapper;
    private final IdempotencyStore store;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public IdempotencyGuard(ObjectMapper objectMapper, StringRedisTemplate redisTemplate, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.objectMapper = objectMapper;
        this.store = new RedisIdempotencyStore(redisTemplate);
        this.meterRegistryProvider = meterRegistryProvider;
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
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, HEADER_IDEMPOTENCY_KEY + " 不能为空");
        }
        String key = normalizeKey(idempotencyKey);
        if (key.length() > 128) {
            record(operation, "invalid_key");
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, HEADER_IDEMPOTENCY_KEY + " 过长");
        }
        if (supplier == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "supplier 不能为空");
        }

        String storeKey = buildStoreKey(operation, userId, key);
        Duration processingTtl = Duration.ofSeconds(30);
        Duration successTtl = Duration.ofHours(24);

        try {
            boolean acquired = store.tryAcquireProcessing(storeKey, processingTtl);
            if (acquired) {
                record(operation, "first_time");
                try {
                    T result = supplier.get();
                    String json = toJson(result);
                    store.save(storeKey, VALUE_SUCCESS_PREFIX + json, successTtl);
                    record(operation, "succeeded");
                    return result;
                } catch (RuntimeException e) {
                    // 失败允许重试：删除占用 key，避免永久卡死在 PROCESSING
                    safeDelete(storeKey);
                    record(operation, "failed");
                    throw e;
                }
            }

            String existing = store.get(storeKey);
            if (existing == null) {
                // 极端情况：刚判断为“已存在”，但随后读不到（可能已过期/被清理）
                record(operation, "race_miss");
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等状态读取失败");
            }
            if (existing.startsWith(VALUE_SUCCESS_PREFIX)) {
                record(operation, "duplicate");
                String json = existing.substring(VALUE_SUCCESS_PREFIX.length());
                return fromJson(json, type);
            }
            if (VALUE_PROCESSING.equals(existing)) {
                record(operation, "concurrent_conflict");
                throw new BusinessException(new SimpleErrorCode(409, "请求处理中，请稍后重试", 409));
            }

            record(operation, "unknown_state");
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等状态不合法");
        } catch (BusinessException e) {
            // 业务异常直接透传
            throw e;
        } catch (Exception e) {
            record(operation, "store_error");
            if (failClosedOnStoreError) {
                throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "幂等存储不可用");
            }
            // fail-open：降级为非幂等执行（仅用于非关键入口）
            return supplier.get();
        }
    }

    private String buildStoreKey(String operation, int userId, String key) {
        String op = operation.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + op + ":" + userId + ":" + key;
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            throw new IllegalStateException("deserialize idempotency response failed", e);
        }
    }

    private void safeDelete(String key) {
        try {
            store.delete(key);
        } catch (Exception ignored) {
        }
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

