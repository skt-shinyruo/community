package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
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

    private ConsumerRecord<Object, Object> recordWithTrace(String topic, long offset, String traceId) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(TraceHeaders.HEADER_TRACE_ID, traceId.getBytes(StandardCharsets.UTF_8));
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
