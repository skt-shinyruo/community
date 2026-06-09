package com.nowcoder.observability.runtimediagnostics.probes.jdbc;

import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyCallKey;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyTextSanitizer;
import net.bytebuddy.asm.Advice;

import java.util.Locale;
import java.util.Map;

public class JdbcStatementAdvice {

    public record JdbcCall(String operation, String statementHash) {
    }

    public static JdbcCall describeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return new JdbcCall("unknown", "unknown");
        }
        String normalized = sql.replaceAll("'[^']*'", "?")
                .replaceAll("\\b\\d+\\b", "?")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        String operation = normalized.split(" ", 2)[0];
        if (!operation.matches("select|insert|update|delete|merge|call")) {
            operation = "unknown";
        }
        return new JdbcCall(operation, DependencyTextSanitizer.hash16(normalized));
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
        JdbcCall call = describeSql(firstSqlArgument(arguments));
        DependencyDiagnosticsRuntime.recordCall(
                "jdbc",
                "jdbc_slow_call",
                "jdbc_call_summary",
                thrown == null ? "success" : "error",
                new DependencyCallKey("jdbc", Map.of(
                        "db.system", "jdbc",
                        "db.operation", call.operation(),
                        "db.statement.hash", call.statementHash()
                )),
                durationMs,
                DependencyDiagnosticsRuntime.thresholdMs("jdbc"),
                thrown != null,
                Map.of()
        );
    }

    private static String firstSqlArgument(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof String value) {
                return value;
            }
        }
        return null;
    }
}
