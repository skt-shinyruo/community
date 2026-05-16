package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceKafkaSenderTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void sendShouldPropagateCurrentTraceHeadersOnProducerRecord() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SpanContext spanContext = SpanContext.create(
                "abababababababababababababababab",
                "1234567890abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
            TraceKafkaSender.send(kafkaTemplate, "topic-a", "key-1", "value-1");
        }

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("topic-a");
        assertThat(record.key()).isEqualTo("key-1");
        assertThat(record.value()).isEqualTo("value-1");
        assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-abababababababababababababababab-1234567890abcdef-01");
    }
}
