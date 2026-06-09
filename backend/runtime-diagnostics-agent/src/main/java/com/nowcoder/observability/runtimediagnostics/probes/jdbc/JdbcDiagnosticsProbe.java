package com.nowcoder.observability.runtimediagnostics.probes.jdbc;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class JdbcDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public void start(ProbeContext context) {
        // Byte Buddy registration is owned by RuntimeDiagnosticsAgent.
    }
}
