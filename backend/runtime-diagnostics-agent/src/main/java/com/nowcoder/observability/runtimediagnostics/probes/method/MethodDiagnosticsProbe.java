package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;

public class MethodDiagnosticsProbe implements Probe {

    private final MethodLatencyAggregator aggregator;
    private volatile MethodSummaryReporter reporter;

    public MethodDiagnosticsProbe(MethodLatencyAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String name() {
        return "method";
    }

    @Override
    public void start(ProbeContext context) {
        MethodSummaryReporter created = new MethodSummaryReporter(
                aggregator,
                context.logger(),
                context.traceContextReader(),
                context.config().topN()
        );
        reporter = created;
        created.start(context.config().summaryInterval());
    }

    @Override
    public void stop() {
        MethodSummaryReporter current = reporter;
        if (current != null) {
            current.stop();
        }
        reporter = null;
    }
}
