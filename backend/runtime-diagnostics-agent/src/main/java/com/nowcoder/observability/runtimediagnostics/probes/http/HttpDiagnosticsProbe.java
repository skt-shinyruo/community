package com.nowcoder.observability.runtimediagnostics.probes.http;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class HttpDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "http";
    }

    @Override
    public void start(ProbeContext context) {
        // Byte Buddy registration is owned by RuntimeDiagnosticsAgent.
    }
}
