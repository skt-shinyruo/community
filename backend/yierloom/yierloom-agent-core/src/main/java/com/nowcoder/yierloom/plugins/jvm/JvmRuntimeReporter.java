package com.nowcoder.yierloom.plugins.jvm;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;

public final class JvmRuntimeReporter {
    private final RuntimeMXBean runtimeMxBean;
    private final MemoryMXBean memoryMxBean;
    private final List<GarbageCollectorMXBean> garbageCollectorMxBeans;
    private final ClassLoadingMXBean classLoadingMxBean;
    private final ThreadMXBean threadMxBean;
    private final IntSupplier availableProcessors;

    public JvmRuntimeReporter() {
        this(
                ManagementFactory.getRuntimeMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getGarbageCollectorMXBeans(),
                ManagementFactory.getClassLoadingMXBean(),
                ManagementFactory.getThreadMXBean(),
                () -> Runtime.getRuntime().availableProcessors());
    }

    public JvmRuntimeReporter(
            RuntimeMXBean runtimeMxBean,
            MemoryMXBean memoryMxBean,
            List<GarbageCollectorMXBean> garbageCollectorMxBeans,
            ClassLoadingMXBean classLoadingMxBean,
            ThreadMXBean threadMxBean
    ) {
        this(
                runtimeMxBean,
                memoryMxBean,
                garbageCollectorMxBeans,
                classLoadingMxBean,
                threadMxBean,
                () -> Runtime.getRuntime().availableProcessors());
    }

    JvmRuntimeReporter(
            RuntimeMXBean runtimeMxBean,
            MemoryMXBean memoryMxBean,
            List<GarbageCollectorMXBean> garbageCollectorMxBeans,
            ClassLoadingMXBean classLoadingMxBean,
            ThreadMXBean threadMxBean,
            IntSupplier availableProcessors
    ) {
        this.runtimeMxBean = Objects.requireNonNull(runtimeMxBean, "runtimeMxBean");
        this.memoryMxBean = Objects.requireNonNull(memoryMxBean, "memoryMxBean");
        this.garbageCollectorMxBeans = garbageCollectorMxBeans == null
                ? List.of()
                : List.copyOf(garbageCollectorMxBeans);
        this.classLoadingMxBean = Objects.requireNonNull(classLoadingMxBean, "classLoadingMxBean");
        this.threadMxBean = Objects.requireNonNull(threadMxBean, "threadMxBean");
        this.availableProcessors = Objects.requireNonNull(
                availableProcessors, "availableProcessors");
    }

    public void report(EventSink events) {
        Objects.requireNonNull(events, "events");
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();
        long totalGcCount = 0;
        long totalGcTimeMs = 0;
        for (GarbageCollectorMXBean garbageCollector : garbageCollectorMxBeans) {
            totalGcCount += nonNegative(garbageCollector.getCollectionCount());
            totalGcTimeMs += nonNegative(garbageCollector.getCollectionTime());
        }

        events.emit(DiagnosticEvent.builder("jvm_runtime_summary")
                .attribute("event.outcome", "success")
                .longField("jvm.uptime.ms", runtimeMxBean.getUptime())
                .longField("jvm.available.processors", availableProcessors.getAsInt())
                .longField("jvm.memory.heap.used.bytes", heap.getUsed())
                .longField("jvm.memory.heap.max.bytes", heap.getMax())
                .longField("jvm.memory.nonheap.used.bytes", nonHeap.getUsed())
                .longField("jvm.thread.count", threadMxBean.getThreadCount())
                .longField("jvm.class.loaded.count", classLoadingMxBean.getLoadedClassCount())
                .longField("jvm.gc.collection.count", totalGcCount)
                .longField("jvm.gc.collection.time.ms", totalGcTimeMs)
                .build());
    }

    private static long nonNegative(long value) {
        return Math.max(0, value);
    }
}
