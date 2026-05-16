package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import com.nowcoder.community.common.trace.TraceIdCodec;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TraceRecordInterceptorTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void interceptorShouldRestoreTraceBeforeListenerAndClearAfterRecord() {
        ConsumerRecord<Object, Object> record = recordWithTrace(
                "topic-a",
                1L,
                "cccccccccccccccccccccccccccccccc"
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        ConsumerRecord<Object, Object> intercepted = interceptor.intercept(record, null);

        assertThat(intercepted).isSameAs(record);
        assertThat(TraceId.get()).isEqualTo("cccccccccccccccccccccccccccccccc");

        interceptor.afterRecord(record, null);

        assertThat(TraceId.get()).isNull();
    }

    @Test
    void failureShouldKeepTraceUntilAfterRecordCleanup() {
        ConsumerRecord<Object, Object> record = recordWithTrace(
                "topic-a",
                1L,
                "dddddddddddddddddddddddddddddddd"
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        interceptor.intercept(record, null);

        assertThat(TraceId.get()).isEqualTo("dddddddddddddddddddddddddddddddd");

        interceptor.failure(record, new RuntimeException("boom"), null);

        assertThat(TraceId.get()).isEqualTo("dddddddddddddddddddddddddddddddd");

        interceptor.afterRecord(record, null);

        assertThat(TraceId.get()).isNull();
    }

    @Test
    void interceptShouldClosePreviousScopeBeforeConsecutiveRecord() {
        ConsumerRecord<Object, Object> recordA = recordWithTrace(
                "topic-a",
                1L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        ConsumerRecord<Object, Object> recordB = recordWithTrace(
                "topic-a",
                2L,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        interceptor.intercept(recordA, null);

        assertThat(TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        interceptor.intercept(recordB, null);

        assertThat(TraceId.get()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        interceptor.afterRecord(recordB, null);

        assertThat(TraceId.get()).isNull();
    }

    @Test
    void interceptorShouldUseRecordTraceWhenAnotherOtelSpanIsActive() {
        ConsumerRecord<Object, Object> record = recordWithTrace(
                "topic-a",
                1L,
                "cccccccccccccccccccccccccccccccc"
        );
        SpanContext activeSpanContext = SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbb",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        try (Scope ignored = Span.wrap(activeSpanContext).makeCurrent()) {
            interceptor.intercept(record, null);

            assertThat(TraceId.get()).isEqualTo("cccccccccccccccccccccccccccccccc");

            interceptor.afterRecord(record, null);
            assertThat(TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }
    }

    @Test
    void interceptorShouldUseRecordParentSpanWhenActiveSpanSharesTrace() {
        String traceId = "cccccccccccccccccccccccccccccccc";
        ConsumerRecord<Object, Object> record = recordWithTrace(
                "topic-a",
                1L,
                traceId,
                "1111111111111111"
        );
        SpanContext activeSpanContext = SpanContext.create(
                traceId,
                "2222222222222222",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        TraceRecordInterceptor interceptor = new TraceRecordInterceptor();

        try (Scope ignored = Span.wrap(activeSpanContext).makeCurrent()) {
            interceptor.intercept(record, null);

            assertThat(TraceId.get()).isEqualTo(traceId);
            assertThat(Span.current().getSpanContext().getSpanId()).isEqualTo("1111111111111111");
            assertThat(org.slf4j.MDC.get(TraceContext.MDC_KEY_SPAN_ID)).isEqualTo("1111111111111111");

            interceptor.afterRecord(record, null);
            assertThat(Span.current().getSpanContext().getSpanId()).isEqualTo("2222222222222222");
        }
    }

    private ConsumerRecord<Object, Object> recordWithTrace(String topic, long offset, String traceId) {
        return recordWithTrace(topic, offset, traceId, "00f067aa0ba902b7");
    }

    private ConsumerRecord<Object, Object> recordWithTrace(String topic, long offset, String traceId, String spanId) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(traceId, spanId, "01")
                .getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>(
                topic,
                0,
                offset,
                0L,
                TimestampType.CREATE_TIME,
                (Long) null,
                0,
                0,
                "key",
                "value",
                headers
        );
    }
}
