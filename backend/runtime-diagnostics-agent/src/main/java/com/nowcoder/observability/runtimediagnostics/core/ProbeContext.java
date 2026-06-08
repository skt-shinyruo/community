package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

public record ProbeContext(
        DiagnosticsConfig config,
        DiagnosticEventLogger logger,
        TraceContextReader traceContextReader
) {

    public static ProbeContext noop() {
        return new ProbeContext(null, new DiagnosticEventLogger(System.out, "noop"), new TraceContextReader());
    }
}
