package com.nowcoder.observability.runtimediagnostics.probes.http;

import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyCallKey;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyTextSanitizer;
import net.bytebuddy.asm.Advice;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

public class HttpExchangeAdvice {

    public static String hashHost(String host) {
        return DependencyTextSanitizer.hash16(host);
    }

    public static String sanitizeRoute(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "unknown";
        }
        try {
            URI uri = URI.create(rawUrl);
            String path = uri.getPath();
            return path == null || path.isBlank() ? "/" : path;
        } catch (RuntimeException ignored) {
            int query = rawUrl.indexOf('?');
            String withoutQuery = query >= 0 ? rawUrl.substring(0, query) : rawUrl;
            return withoutQuery.isBlank() ? "unknown" : withoutQuery;
        }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.AllArguments Object[] arguments,
            @Advice.Enter long startedAtNanos,
            @Advice.Thrown Throwable thrown
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        HttpCall call = describeRequest(firstArgument(arguments));
        DependencyDiagnosticsRuntime.recordCall(
                "http",
                "http_slow_call",
                "http_call_summary",
                thrown == null ? "success" : "error",
                new DependencyCallKey("http", Map.of(
                        "http.direction", "outbound",
                        "http.method", call.method(),
                        "http.route", call.route(),
                        "network.peer.name.hash", call.hostHash()
                )),
                durationMs,
                DependencyDiagnosticsRuntime.thresholdMs("http"),
                thrown != null,
                Map.of()
        );
    }

    private static Object firstArgument(Object[] arguments) {
        return arguments == null || arguments.length == 0 ? null : arguments[0];
    }

    private static HttpCall describeRequest(Object request) {
        String method = safeMethod(invokeString(request, "method"));
        String url = invokeString(request, "url");
        return new HttpCall(method, sanitizeRoute(url), hashHost(host(url)));
    }

    private static String safeMethod(String value) {
        return value == null || value.isBlank()
                ? "UNKNOWN"
                : value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
    }

    private static String host(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawUrl).getHost();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private record HttpCall(String method, String route, String hostHash) {
    }
}
