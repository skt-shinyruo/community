package com.nowcoder.observability.methodprofiler.schedule;

import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;

import java.time.Duration;

public class SummaryReporter {

    private final MethodLatencyAggregator aggregator;
    private final ProfilerEventLogger logger;
    private final TraceContextReader traceContextReader;
    private final int topN;

    public SummaryReporter(
            MethodLatencyAggregator aggregator,
            ProfilerEventLogger logger,
            TraceContextReader traceContextReader,
            int topN
    ) {
        this.aggregator = aggregator;
        this.logger = logger;
        this.traceContextReader = traceContextReader;
        this.topN = topN;
    }

    public void start(Duration interval) {
        Thread thread = new Thread(() -> runLoop(interval), "method-profiler-summary");
        thread.setDaemon(true);
        thread.start();
    }

    public void reportOnce() {
        logger.logSummary(aggregator.topSnapshots(topN), aggregator.droppedMethodKeys(), traceContextReader.currentTraceFields());
    }

    private void runLoop(Duration interval) {
        long sleepMillis = Math.max(1_000, interval.toMillis());
        while (true) {
            try {
                Thread.sleep(sleepMillis);
                reportOnce();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }
}
