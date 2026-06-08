package com.nowcoder.observability.runtimediagnostics;

import java.lang.instrument.Instrumentation;

public final class RuntimeDiagnosticsAgent {

    private RuntimeDiagnosticsAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        if (instrumentation == null) {
            return;
        }
    }
}
