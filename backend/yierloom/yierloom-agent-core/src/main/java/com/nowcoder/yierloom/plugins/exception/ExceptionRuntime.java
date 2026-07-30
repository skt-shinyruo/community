package com.nowcoder.yierloom.plugins.exception;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ObservationHandler;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.plugins.support.SamplingDecider;
import com.nowcoder.yierloom.plugins.support.TokenBucketRateLimiter;

public final class ExceptionRuntime implements ObservationHandler, AutoCloseable {
    private static final String OBSERVATION_TYPE = "exception-thrown";
    private static final int MAX_IDENTITY_LENGTH = 240;

    private final EventSink events;
    private final SamplingDecider sampling;
    private final TokenBucketRateLimiter rateLimiter;
    private final int maxTrackedKeys;
    private final ConcurrentMap<MethodIdentity, MethodKey> methodKeys =
            new ConcurrentHashMap<>();
    private final Object emissionLock = new Object();

    private volatile boolean active = true;

    private ExceptionRuntime(
            EventSink events,
            double sampleRate,
            int maxEventsPerSecond,
            int maxTrackedKeys
    ) {
        this.events = Objects.requireNonNull(events, "events");
        this.sampling = new SamplingDecider(sampleRate);
        this.rateLimiter = new TokenBucketRateLimiter(maxEventsPerSecond);
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public static ExceptionRuntime start(
            PluginRuntimeContext context,
            ExceptionPlugin.ExceptionSettings settings
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        ExceptionRuntime runtime = new ExceptionRuntime(
                context.events(),
                settings.sampleRate(),
                settings.maxEventsPerSecond(),
                settings.maxTrackedKeys());
        try {
            context.observations().register(runtime);
            return runtime;
        } catch (Throwable failure) {
            runtime.close();
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("exception runtime could not start", failure);
        }
    }

    @Override
    public void onObservation(PluginObservation observation) {
        if (!active || observation == null || !OBSERVATION_TYPE.equals(observation.type())) {
            return;
        }

        Map<String, String> attributes = observation.attributes();
        String className = attributes.get("method.class");
        String methodName = attributes.get("method.name");
        String descriptor = attributes.get("method.descriptor");
        String exceptionType = attributes.get("exception.type");
        if (className == null
                || methodName == null
                || descriptor == null
                || exceptionType == null
                || exceptionType.isBlank()
                || !sampling.sample()) {
            return;
        }

        MethodKey methodKey = methodKey(className, methodName, descriptor);
        if (methodKey == null || !rateLimiter.tryAcquire() || !active) {
            return;
        }

        DiagnosticEvent.Builder event = DiagnosticEvent.builder("exception_observed")
                .attribute("event.outcome", "error")
                .attribute("exception.type", exceptionType)
                .attribute("method.class", methodKey.className())
                .attribute("method.name", methodKey.methodName())
                .attribute("method.signature.hash", methodKey.signatureHash());
        addOptionalAttribute(event, "trace.id", attributes.get("trace.id"));
        addOptionalAttribute(event, "span.id", attributes.get("span.id"));
        synchronized (emissionLock) {
            if (active) {
                events.emit(event.build());
            }
        }
    }

    private MethodKey methodKey(String className, String methodName, String descriptor) {
        MethodIdentity identity = MethodIdentity.from(className, methodName, descriptor);
        MethodKey existing = methodKeys.get(identity);
        if (existing != null) {
            return existing;
        }
        synchronized (methodKeys) {
            existing = methodKeys.get(identity);
            if (existing != null) {
                return existing;
            }
            if (methodKeys.size() >= maxTrackedKeys) {
                return null;
            }
            MethodKey created = MethodKey.from(identity);
            methodKeys.put(identity, created);
            return created;
        }
    }

    private static void addOptionalAttribute(
            DiagnosticEvent.Builder event,
            String key,
            String value
    ) {
        if (value != null && !value.isBlank()) {
            event.attribute(key, value);
        }
    }

    @Override
    public void close() {
        active = false;
        synchronized (emissionLock) {
            // Wait for an event admitted before the active flag changed.
        }
        synchronized (methodKeys) {
            methodKeys.clear();
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= MAX_IDENTITY_LENGTH
                ? value
                : value.substring(0, MAX_IDENTITY_LENGTH);
    }

    private static String signatureHash(MethodIdentity identity) {
        String signature = identity.className()
                + "#" + identity.methodName()
                + ":" + identity.descriptor();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record MethodIdentity(String className, String methodName, String descriptor) {
        private static MethodIdentity from(
                String className,
                String methodName,
                String descriptor
        ) {
            return new MethodIdentity(
                    sanitize(className),
                    sanitize(methodName),
                    sanitize(descriptor));
        }
    }

    private record MethodKey(String className, String methodName, String signatureHash) {
        private static MethodKey from(MethodIdentity identity) {
            return new MethodKey(
                    identity.className(),
                    identity.methodName(),
                    ExceptionRuntime.signatureHash(identity));
        }
    }
}
