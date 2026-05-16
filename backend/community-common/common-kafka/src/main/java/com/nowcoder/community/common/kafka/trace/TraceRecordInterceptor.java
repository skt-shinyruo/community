package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextScope;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

public class TraceRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final ThreadLocal<TraceContextScope> currentScope = new ThreadLocal<>();

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        closeCurrentScope();
        TraceContextSnapshot snapshot = record == null
                ? TraceContextSnapshot.currentOrNew()
                : TraceKafkaHeaders.extract(record.headers());
        String spanName = record == null ? "kafka.consume" : "kafka.consume " + record.topic();
        currentScope.set(OtelTraceContext.openForInbound(snapshot.traceparent(), spanName, SpanKind.CONSUMER));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        closeCurrentScope();
    }

    private void closeCurrentScope() {
        TraceContextScope scope = currentScope.get();
        currentScope.remove();
        if (scope != null) {
            scope.close();
        }
    }
}
