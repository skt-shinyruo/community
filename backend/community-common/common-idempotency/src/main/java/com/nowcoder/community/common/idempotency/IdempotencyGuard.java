package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.exception.ErrorCode;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
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
        return execute(operation, userId, idempotencyKey, requestHash, replayConflictCode, type, supplier);
    }

    private <T> T execute(String operation,
                          UUID userId,
                          String idempotencyKey,
                          String requestHash,
                          ErrorCode replayConflictCode,
                          Class<T> type,
                          Supplier<T> supplier) {
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
        Duration processingTtl = safeDuration(properties == null ? null : properties.getProcessingTtl(), Duration.ofSeconds(30));
        Duration successTtl = safeDuration(properties == null ? null : properties.getSuccessTtl(), Duration.ofHours(24));
        String op = operation.trim().toLowerCase(Locale.ROOT);
        String hash = normalizeRequestHash(requestHash);
        if (supplier == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "supplier 不能为空");
        }
        if (!(store instanceof TransactionalIdempotencyStore transactional)
                || !transactional.isEnlistedInCurrentTransaction()) {
            record(operation, "store_unavailable");
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
        }

        boolean acquired;
        try {
            acquired = store.tryAcquireProcessing(op, userId, key, hash, processingTtl);
        } catch (RuntimeException e) {
            record(operation, "store_unavailable");
            throw e;
        }

        if (acquired) {
            record(operation, "first_time");
            T result;
            try {
                result = supplier.get();
            } catch (RuntimeException e) {
                record(operation, "failed");
                throw e;
            }
            String json;
            try {
                json = toJson(result);
            } catch (RuntimeException e) {
                record(operation, "serialize_error");
                throw e;
            }
            boolean saved;
            try {
                saved = store.saveSuccess(op, userId, key, hash, json, successTtl);
            } catch (RuntimeException e) {
                record(operation, "store_unavailable");
                throw e;
            }
            if (!saved) {
                record(operation, "store_unavailable");
                throw new IllegalStateException("idempotency success transition was not applied");
            }
            record(operation, "succeeded");
            return result;
        }

        IdempotencyStore.Entry existing;
        try {
            existing = store.get(op, userId, key);
        } catch (RuntimeException e) {
            record(operation, "store_unavailable");
            throw e;
        }
        if (existing == null) {
            record(operation, "race_miss");
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
        }
        if (!hash.equals(existing.requestHash())) {
            record(operation, "replay_conflict");
            throw replayConflict(replayConflictCode);
        }
        if (existing.status() == IdempotencyStore.Status.SUCCESS) {
            record(operation, "duplicate");
            try {
                return fromJson(existing.successJson(), type);
            } catch (BusinessException e) {
                record(operation, "store_unavailable");
                throw e;
            }
        }
        if (existing.status() == IdempotencyStore.Status.PROCESSING) {
            record(operation, "concurrent_conflict");
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        if (existing.status() == IdempotencyStore.Status.INDETERMINATE) {
            record(operation, "indeterminate");
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_OUTCOME_INDETERMINATE);
        }

        record(operation, "unknown_state");
        throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
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
            throw new IllegalStateException("idempotency response is null");
        }
        if (jsonCodec == null) {
            throw new IllegalStateException("jsonCodec is null");
        }
        String json;
        try {
            json = jsonCodec.toJson(value);
        } catch (JsonCodecException e) {
            throw new IllegalStateException("serialize idempotency response failed", e);
        }
        if (!StringUtils.hasText(json) || "null".equals(json.trim())) {
            throw new IllegalStateException("serialized idempotency response is empty or null");
        }
        return json;
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (!StringUtils.hasText(json) || "null".equals(json.trim())) {
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
        }
        if (type == null) {
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
        }
        try {
            if (jsonCodec == null) {
                throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
            }
            T value = jsonCodec.fromJson(json, type);
            if (value == null) {
                throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
            }
            return value;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BusinessException(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE, e);
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
