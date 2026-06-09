package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEvent;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

import java.time.Duration;

public class MethodSummaryReporter {

    private final MethodLatencyAggregator aggregator;
    private final DiagnosticEventLogger logger;
    private final TraceContextReader traceContextReader;
    private final int topN;
    private volatile Thread thread;

    public MethodSummaryReporter(
            MethodLatencyAggregator aggregator,
            DiagnosticEventLogger logger,
            TraceContextReader traceContextReader,
            int topN
    ) {
        this.aggregator = aggregator;
        this.logger = logger;
        this.traceContextReader = traceContextReader;
        this.topN = topN;
    }

    public Thread start(Duration interval) {
        Thread thread = new Thread(() -> runLoop(interval), "runtime-diagnostics-method-summary");
        thread.setDaemon(true);
        this.thread = thread;
        thread.start();
        return thread;
    }

    public void stop() {
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
        thread = null;
    }

    public void reportOnce() {
        long droppedMethodKeys = aggregator.droppedMethodKeys();
        for (MethodSnapshot snapshot : aggregator.topSnapshots(topN)) {
            DiagnosticEvent.Builder builder = DiagnosticEvent.builder("method_latency_summary", "success", "method")
                    .put("method.class", snapshot.key().className())
                    .put("method.name", snapshot.key().methodName())
                    .put("method.signature.hash", snapshot.key().signatureHash())
                    .put("method.invocation.count", snapshot.count())
                    .put("duration.avg.ms", snapshot.avgMs())
                    .put("duration.max.ms", snapshot.maxMs())
                    .put("duration.p95.ms", snapshot.p95Ms());
            if (droppedMethodKeys > 0) {
                builder.put("method.dropped.keys", droppedMethodKeys);
            }
            logger.log(builder
                    .putTraceFields(traceContextReader.currentTraceFields())
                    .build());
        }
    }

    private void runLoop(Duration interval) {
        long sleepMillis = Math.max(1_000, interval.toMillis());
        while (true) {
            try {
                Thread.sleep(sleepMillis);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                reportOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }
}
