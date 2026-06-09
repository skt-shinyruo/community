package com.nowcoder.observability.runtimediagnostics.probes.kafka;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class KafkaDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "kafka";
    }

    @Override
    public void start(ProbeContext context) {
        // Byte Buddy registration is owned by RuntimeDiagnosticsAgent.
    }
}
