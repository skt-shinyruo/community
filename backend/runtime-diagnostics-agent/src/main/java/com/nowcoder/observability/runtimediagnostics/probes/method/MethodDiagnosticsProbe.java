package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class MethodDiagnosticsProbe implements Probe {

    private final MethodLatencyAggregator aggregator;

    public MethodDiagnosticsProbe(MethodLatencyAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String name() {
        return "method";
    }

    @Override
    public void start(ProbeContext context) {
        new MethodSummaryReporter(aggregator, context.logger(), context.traceContextReader(), context.config().topN())
                .start(context.config().summaryInterval());
    }
}
