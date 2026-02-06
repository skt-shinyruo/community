package com.nowcoder.community.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import org.springframework.util.StringUtils;

/**
 * Kafka 消费侧 traceId 注入工具：
 * - 从事件 envelope（JSON）读取 traceId 写入 TraceContext/MDC
 * - finally 清理，避免线程复用导致串线
 */
public final class KafkaTraceSupport {

    private KafkaTraceSupport() {
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run();
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get();
    }

    public static void runWithTraceId(ObjectMapper objectMapper, String recordValue, ThrowingRunnable runnable) {
        String traceId = resolveTraceId(objectMapper, recordValue);
        TraceContext.set(traceId);
        try {
            runnable.run();
        } finally {
            TraceContext.clear();
        }
    }

    public static <T> T callWithTraceId(ObjectMapper objectMapper, String recordValue, ThrowingSupplier<T> supplier) {
        String traceId = resolveTraceId(objectMapper, recordValue);
        TraceContext.set(traceId);
        try {
            return supplier.get();
        } finally {
            TraceContext.clear();
        }
    }

    public static String resolveTraceId(ObjectMapper objectMapper, String recordValue) {
        if (objectMapper != null && StringUtils.hasText(recordValue)) {
            try {
                JsonNode root = objectMapper.readTree(recordValue);
                String traceId = text(root, "traceId");
                if (StringUtils.hasText(traceId)) {
                    return traceId;
                }
            } catch (JsonProcessingException | RuntimeException ignore) {
                // ignore
            }
        }
        // 非 HTTP 场景也保证有 traceId，便于日志串联
        return TraceId.generate();
    }

    private static String text(JsonNode root, String field) {
        if (root == null || !StringUtils.hasText(field)) {
            return null;
        }
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
