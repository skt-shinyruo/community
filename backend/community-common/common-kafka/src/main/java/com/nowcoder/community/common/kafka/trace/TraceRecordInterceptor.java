package com.nowcoder.community.common.kafka.trace;

import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextScope;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.ArrayList;
import java.util.List;

public class TraceRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final ThreadLocal<TraceContextScope> currentScope = new ThreadLocal<>();

    /**
     * Builds a composite interceptor with a fresh {@link TraceRecordInterceptor} first,
     * followed by the given delegates in {@link org.springframework.core.Ordered} order.
     * Nulls and {@code TraceRecordInterceptor} instances among the delegates are skipped.
     */
    @SuppressWarnings("rawtypes")
    public static CompositeRecordInterceptor composeFirst(RecordInterceptor<?, ?>... delegates) {
        List<RecordInterceptor<?, ?>> ordered = new ArrayList<>();
        if (delegates != null) {
            for (RecordInterceptor<?, ?> delegate : delegates) {
                if (delegate != null && !(delegate instanceof TraceRecordInterceptor)) {
                    ordered.add(delegate);
                }
            }
        }
        ordered.sort(AnnotationAwareOrderComparator.INSTANCE);
        ordered.add(0, new TraceRecordInterceptor());
        return new CompositeRecordInterceptor(ordered.toArray(new RecordInterceptor[0]));
    }

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
