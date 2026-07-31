package com.nowcoder.yierloom.plugins.http;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class HttpObservationHelper {
    private static final String CLIENT_REQUEST =
            "org.springframework.web.reactive.function.client.ClientRequest";
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private HttpObservationHelper() {
    }

    public static void observe(Object request, long durationMs, boolean error) {
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            YierLoomBridge.observe("http", describe(request, durationMs, error));
        } finally {
            OBSERVING.remove();
        }
    }

    public static PluginObservation describe(Object request, long durationMs, boolean error) {
        request = firstArgument(request);
        Object methodValue = invokeClientRequest(request, "method");
        Object urlValue = invokeClientRequest(request, "url");
        URI uri = asUri(urlValue);
        PluginObservation.Builder observation = PluginObservation.builder("dependency-call")
                .attribute("http.direction", "outbound")
                .attribute("http.method", normalizeMethod(methodValue))
                .attribute("http.route", route(uri))
                .attribute("network.peer.name.hash", hash16(uri == null ? null : uri.getHost()))
                .longField("duration.ms", Math.max(0, durationMs))
                .booleanField("error", error);
        addTraceFields(observation, currentTraceFields());
        return observation.build();
    }

    private static Object invokeClientRequest(Object request, String methodName) {
        if (request == null) {
            return null;
        }
        try {
            ClassLoader loader = request.getClass().getClassLoader();
            Class<?> requestType = Class.forName(CLIENT_REQUEST, false, loader);
            if (!requestType.isInstance(request)) {
                return invokePublic(request, methodName);
            }
            return requestType.getMethod(methodName).invoke(request);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return invokePublic(request, methodName);
        }
    }

    private static Object invokePublic(Object request, String methodName) {
        try {
            Method interfaceMethod = findPublicInterfaceMethod(request.getClass(), methodName);
            return interfaceMethod == null
                    ? request.getClass().getMethod(methodName).invoke(request)
                    : interfaceMethod.invoke(request);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findPublicInterfaceMethod(Class<?> type, String methodName) {
        if (type == null) {
            return null;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (Modifier.isPublic(interfaceType.getModifiers())) {
                try {
                    return interfaceType.getMethod(methodName);
                } catch (NoSuchMethodException ignored) {
                }
            }
            Method inherited = findPublicInterfaceMethod(interfaceType, methodName);
            if (inherited != null) {
                return inherited;
            }
        }
        return findPublicInterfaceMethod(type.getSuperclass(), methodName);
    }

    private static Object firstArgument(Object request) {
        if (!(request instanceof Object[] arguments)) {
            return request;
        }
        return arguments.length == 0 ? null : arguments[0];
    }

    private static URI asUri(Object value) {
        if (value instanceof URI uri) {
            return uri;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return URI.create(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String normalizeMethod(Object value) {
        if (value == null) {
            return "UNKNOWN";
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return "UNKNOWN";
        }
        return text.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
    }

    private static String route(URI uri) {
        if (uri == null) {
            return "unknown";
        }
        String path = uri.getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private static String hash16(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void addTraceFields(PluginObservation.Builder observation, String[] fields) {
        if (fields[0] != null && !fields[0].isBlank()) {
            observation.attribute("trace.id", fields[0]);
        }
        if (fields[1] != null && !fields[1].isBlank()) {
            observation.attribute("span.id", fields[1]);
        }
    }

    private static String[] currentTraceFields() {
        String[] fields = readOpenTelemetry();
        return fields[0] == null ? readMdc() : fields;
    }

    private static String[] readOpenTelemetry() {
        try {
            Class<?> spanClass = loadClass("io.opentelemetry.api.trace.Span");
            Object span = spanClass.getMethod("current").invoke(null);
            Object spanContext = spanClass.getMethod("getSpanContext").invoke(span);
            Class<?> contextClass = loadClass("io.opentelemetry.api.trace.SpanContext");
            if (!Boolean.TRUE.equals(contextClass.getMethod("isValid").invoke(spanContext))) {
                return new String[2];
            }
            return new String[]{
                    asNonBlankString(contextClass.getMethod("getTraceId").invoke(spanContext)),
                    asNonBlankString(contextClass.getMethod("getSpanId").invoke(spanContext))
            };
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return new String[2];
        }
    }

    private static String[] readMdc() {
        try {
            Class<?> mdcClass = loadClass("org.slf4j.MDC");
            Method get = mdcClass.getMethod("get", String.class);
            return new String[]{
                    asNonBlankString(get.invoke(null, "trace.id")),
                    asNonBlankString(get.invoke(null, "span.id"))
            };
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return new String[2];
        }
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className);
    }

    private static String asNonBlankString(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
