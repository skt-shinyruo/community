package com.nowcoder.observability.runtimediagnostics.probes.jvm;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEvent;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.core.Probe;
import com.nowcoder.observability.runtimediagnostics.core.ProbeContext;
import com.nowcoder.observability.runtimediagnostics.core.ScheduledProbeSupport;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class JvmDiagnosticsProbe implements Probe {

    private final RuntimeMXBean runtimeMxBean;
    private final MemoryMXBean memoryMxBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMxBeans;
    private final ClassLoadingMXBean classLoadingMxBean;
    private final ThreadMXBean threadMxBean;

    public JvmDiagnosticsProbe() {
        this(
                ManagementFactory.getRuntimeMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getGarbageCollectorMXBeans(),
                ManagementFactory.getClassLoadingMXBean(),
                ManagementFactory.getThreadMXBean()
        );
    }

    public JvmDiagnosticsProbe(
            RuntimeMXBean runtimeMxBean,
            MemoryMXBean memoryMxBean,
            List<GarbageCollectorMXBean> garbageCollectorMxBeans,
            ClassLoadingMXBean classLoadingMxBean,
            ThreadMXBean threadMxBean
    ) {
        this.runtimeMxBean = runtimeMxBean;
        this.memoryMxBean = memoryMxBean;
        this.garbageCollectorMxBeans = garbageCollectorMxBeans == null
                ? List.of()
                : List.copyOf(garbageCollectorMxBeans);
        this.classLoadingMxBean = classLoadingMxBean;
        this.threadMxBean = threadMxBean;
    }

    @Override
    public String name() {
        return "jvm";
    }

    @Override
    public void start(ProbeContext context) {
        ScheduledProbeSupport.startDaemon(
                "runtime-diagnostics-jvm-summary",
                context.config().jvmSummaryInterval(),
                () -> reportOnce(context.logger())
        );
    }

    public void reportOnce(DiagnosticEventLogger logger) {
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();
        long totalGcCount = 0;
        long totalGcTimeMs = 0;
        for (GarbageCollectorMXBean garbageCollectorMxBean : garbageCollectorMxBeans) {
            totalGcCount += nonNegative(garbageCollectorMxBean.getCollectionCount());
            totalGcTimeMs += nonNegative(garbageCollectorMxBean.getCollectionTime());
        }

        DiagnosticEvent event = DiagnosticEvent.builder("jvm_runtime_summary", "success", "jvm")
                .put("jvm.uptime.ms", runtimeMxBean.getUptime())
                .put("jvm.available.processors", Runtime.getRuntime().availableProcessors())
                .put("jvm.memory.heap.used.bytes", heap.getUsed())
                .put("jvm.memory.heap.max.bytes", heap.getMax())
                .put("jvm.memory.nonheap.used.bytes", nonHeap.getUsed())
                .put("jvm.thread.count", threadMxBean.getThreadCount())
                .put("jvm.class.loaded.count", classLoadingMxBean.getLoadedClassCount())
                .put("jvm.gc.collection.count", totalGcCount)
                .put("jvm.gc.collection.time.ms", totalGcTimeMs)
                .build();
        logger.log(event);
    }

    private long nonNegative(long value) {
        return Math.max(0, value);
    }
}
