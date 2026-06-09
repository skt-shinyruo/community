package com.nowcoder.observability.runtimediagnostics.probes.redis;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class RedisDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "redis";
    }

    @Override
    public void start(ProbeContext context) {
        // Byte Buddy registration is owned by RuntimeDiagnosticsAgent.
    }
}
