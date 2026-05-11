package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TraceKafkaHeadersTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void injectShouldWriteTraceHeadersToProducerRecord() {
        TraceContext.set("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ProducerRecord<String, Object> record = new ProducerRecord<>("topic-a", "key-1", "value-1");

        TraceKafkaHeaders.inject(record.headers(), TraceContextSnapshot.currentOrNew());

        assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACEPARENT))
                .startsWith("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-");
    }

    @Test
    void extractShouldUseTraceparent() {
        ProducerRecord<String, Object> record = new ProducerRecord<>("topic-a", "key-1", "value-1");
        record.headers().add(
                TraceHeaders.HEADER_TRACEPARENT,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8)
        );

        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(record.headers());

        assertThat(snapshot.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(snapshot.recovered()).isFalse();
    }

    @Test
    void extractShouldRecoverWhenHeadersAreMissing() {
        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(new RecordHeaders());

        assertThat(snapshot.traceId()).matches("[0-9a-f]{32}");
        assertThat(snapshot.traceparent()).startsWith("00-" + snapshot.traceId() + "-");
        assertThat(snapshot.recovered()).isTrue();
    }

    @Test
    void extractShouldRecoverWhenHeadersAreInvalid() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(TraceHeaders.HEADER_TRACEPARENT, "00-not-a-trace-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));

        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(headers);

        assertThat(snapshot.traceId()).matches("[0-9a-f]{32}");
        assertThat(snapshot.traceparent()).startsWith("00-" + snapshot.traceId() + "-");
        assertThat(snapshot.recovered()).isTrue();
    }

    @Test
    void extractShouldGenerateTraceContextWhenTraceparentIsInvalid() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(
                TraceHeaders.HEADER_TRACEPARENT,
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01".getBytes(StandardCharsets.UTF_8)
        );

        TraceContextSnapshot snapshot = TraceKafkaHeaders.extract(headers);

        assertThat(snapshot.traceId()).matches("[0-9a-f]{32}");
        assertThat(snapshot.traceparent()).startsWith("00-" + snapshot.traceId() + "-");
        assertThat(snapshot.recovered()).isTrue();
    }
}
