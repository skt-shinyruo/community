package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;

import java.util.ArrayList;
import java.util.List;

public class ProbeRegistry {

    private final List<Probe> probes;
    private final List<String> disabledProbeNames = new ArrayList<>();
    private final List<Probe> startedProbes = new ArrayList<>();

    public ProbeRegistry(List<Probe> probes) {
        this.probes = probes == null ? List.of() : List.copyOf(probes);
    }

    public void startEnabled(DiagnosticsConfig config, ProbeContext context) {
        stopStarted();
        disabledProbeNames.clear();
        for (Probe probe : probes) {
            if (probe == null || config == null || !config.probeEnabled(probe.name())) {
                continue;
            }
            try {
                probe.start(context);
                startedProbes.add(probe);
            } catch (Throwable ignored) {
                stopQuietly(probe);
                disabledProbeNames.add(probe.name());
            }
        }
    }

    public void stopStarted() {
        for (int i = startedProbes.size() - 1; i >= 0; i--) {
            stopQuietly(startedProbes.get(i));
        }
        startedProbes.clear();
    }

    public List<String> disabledProbeNames() {
        return List.copyOf(disabledProbeNames);
    }

    private void stopQuietly(Probe probe) {
        try {
            if (probe != null) {
                probe.stop();
            }
        } catch (Throwable ignored) {
        }
    }
}
