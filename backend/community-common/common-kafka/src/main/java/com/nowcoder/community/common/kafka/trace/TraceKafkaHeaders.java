package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

public final class TraceKafkaHeaders {

    private TraceKafkaHeaders() {
    }

    public static void inject(Headers headers, TraceContextSnapshot snapshot) {
        if (headers == null || snapshot == null) {
            return;
        }
        put(headers, TraceHeaders.HEADER_TRACE_ID, snapshot.traceId());
        put(headers, TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
    }

    public static TraceContextSnapshot extract(Headers headers) {
        String traceparent = headerValue(headers, TraceHeaders.HEADER_TRACEPARENT);
        String traceId = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        if (traceId != null) {
            return TraceContextSnapshot.fromStored(traceId, traceparent);
        }

        traceId = TraceIdCodec.normalizeTraceId(headerValue(headers, TraceHeaders.HEADER_TRACE_ID));
        return TraceContextSnapshot.fromStored(traceId, null);
    }

    public static String headerValue(Headers headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return null;
        }
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void put(Headers headers, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        headers.remove(name);
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
