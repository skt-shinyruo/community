package com.nowcoder.observability.runtimediagnostics.probes.exception;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class ExceptionDiagnosticsProbe implements Probe {

    @Override
    public String name() {
        return "exception";
    }

    @Override
    public void start(ProbeContext context) {
        // Exception events are emitted by method advice when this probe is enabled.
    }
}
