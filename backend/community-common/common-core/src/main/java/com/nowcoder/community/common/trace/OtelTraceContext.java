package com.nowcoder.community.common.trace;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OtelTraceContext {

    public static final String INSTRUMENTATION_NAME = "com.nowcoder.community";

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? List.of() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            if (carrier == null || key == null) {
                return null;
            }
            return carrier.get(key);
        }
    };

    private static final TextMapSetter<Map<String, String>> MAP_SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.put(key, value);
        }
    };

    private OtelTraceContext() {
    }

    public static SpanContext currentSpanContext() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext : null;
    }

    public static String currentTraceId() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : spanContext.getTraceId();
    }

    public static String currentSpanId() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : spanContext.getSpanId();
    }

    public static String currentTraceparent() {
        SpanContext spanContext = currentSpanContext();
        return spanContext == null ? null : traceparent(spanContext);
    }

    public static String traceparent(SpanContext spanContext) {
        if (spanContext == null || !spanContext.isValid()) {
            return null;
        }
        return TraceIdCodec.buildTraceparent(
                spanContext.getTraceId(),
                spanContext.getSpanId(),
                spanContext.getTraceFlags().asHex()
        );
    }

    public static Context extract(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Context.current();
        }
        return W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), Map.of(TraceHeaders.HEADER_TRACEPARENT, traceparent.trim()), MAP_GETTER);
    }

    public static void inject(Context context, Map<String, String> carrier) {
        W3CTraceContextPropagator.getInstance()
                .inject(context == null ? Context.current() : context, carrier, MAP_SETTER);
    }

    public static TraceContextScope openForInbound(String traceparent, String spanName, SpanKind spanKind) {
        SpanContext active = currentSpanContext();
        Context parent = extract(traceparent);
        SpanContext extracted = Span.fromContext(parent).getSpanContext();
        if (active != null && !shouldSwitchToExtracted(active, extracted)) {
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(active, false), Scope.noop(), null);
        }

        SpanBuilder builder = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder(safeSpanName(spanName))
                .setSpanKind(spanKind == null ? SpanKind.INTERNAL : spanKind);
        if (extracted.isValid()) {
            builder.setParent(parent);
        } else {
            builder.setNoParent();
        }

        Span span = builder.startSpan();
        SpanContext started = span.getSpanContext();
        if (started.isValid()) {
            Scope scope = span.makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(started, false), scope, span);
        }

        if (extracted.isValid()) {
            Scope scope = Span.wrap(extracted).makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(extracted, true), scope, null);
        }

        return TraceContextScope.open(TraceContextSnapshot.synthetic(), Scope.noop(), null);
    }

    public static TraceContextScope openInternalSpan(String spanName) {
        SpanContext active = currentSpanContext();
        SpanBuilder builder = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME)
                .spanBuilder(safeSpanName(spanName))
                .setSpanKind(SpanKind.INTERNAL);
        if (active == null) {
            builder.setNoParent();
        }
        Span span = builder.startSpan();
        SpanContext started = span.getSpanContext();
        if (started.isValid()) {
            Scope scope = span.makeCurrent();
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(started, false), scope, span);
        }
        if (active != null) {
            return TraceContextScope.open(TraceContextSnapshot.fromSpanContext(active, false), Scope.noop(), null);
        }
        return TraceContextScope.open(TraceContextSnapshot.synthetic(), Scope.noop(), null);
    }

    private static String safeSpanName(String spanName) {
        if (spanName == null || spanName.isBlank()) {
            return "community.internal";
        }
        return spanName.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean shouldSwitchToExtracted(SpanContext active, SpanContext extracted) {
        return active != null
                && extracted != null
                && extracted.isValid()
                && (!extracted.getTraceId().equals(active.getTraceId())
                || !extracted.getSpanId().equals(active.getSpanId()));
    }
}
