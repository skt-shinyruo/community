package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;

import java.util.ArrayList;
import java.util.List;

public class ProbeRegistry {

    private final List<Probe> probes;
    private final List<String> disabledProbeNames = new ArrayList<>();

    public ProbeRegistry(List<Probe> probes) {
        this.probes = probes == null ? List.of() : List.copyOf(probes);
    }

    public void startEnabled(DiagnosticsConfig config, ProbeContext context) {
        disabledProbeNames.clear();
        for (Probe probe : probes) {
            if (probe == null || config == null || !config.probeEnabled(probe.name())) {
                continue;
            }
            try {
                probe.start(context);
            } catch (Throwable ignored) {
                disabledProbeNames.add(probe.name());
            }
        }
    }

    public List<String> disabledProbeNames() {
        return List.copyOf(disabledProbeNames);
    }
}
