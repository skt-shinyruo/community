package com.nowcoder.community.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static void runWithTraceId(ObjectMapper objectMapper, String recordValue, ThrowingRunnable runnable) {
        String traceId = resolveTraceId(objectMapper, recordValue);
        TraceContext.set(traceId);
        try {
            runnable.run();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            TraceContext.clear();
        }
    }

    public static <T> T callWithTraceId(ObjectMapper objectMapper, String recordValue, ThrowingSupplier<T> supplier) {
        String traceId = resolveTraceId(objectMapper, recordValue);
        TraceContext.set(traceId);
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
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
            } catch (Exception ignore) {
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
