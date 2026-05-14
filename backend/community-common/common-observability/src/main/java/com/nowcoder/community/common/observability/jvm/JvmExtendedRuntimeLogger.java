package com.nowcoder.community.common.observability.jvm;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

public class JvmExtendedRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final AtomicLong lastLoadedClassCount = new AtomicLong(-1);

    public JvmExtendedRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public void logExtendedSnapshots() {
        logDirectMemorySnapshot();
        logClassLoadingSnapshot();
    }

    private void logDirectMemorySnapshot() {
        try {
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if ("direct".equalsIgnoreCase(pool.getName())) {
                    logDirectMemoryPressure(pool.getMemoryUsed(), pool.getTotalCapacity());
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect application work.
        }
    }

    private void logClassLoadingSnapshot() {
        try {
            ClassLoadingMXBean classLoading = ManagementFactory.getClassLoadingMXBean();
            long loaded = classLoading.getTotalLoadedClassCount();
            long previous = lastLoadedClassCount.getAndSet(loaded);
            long delta = previous < 0 ? loaded : loaded - previous;
            if (delta > 0) {
                logClassLoadingSummary(loaded, classLoading.getUnloadedClassCount(), delta);
            }
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect application work.
        }
    }

    public boolean logDirectMemoryPressure(long usedBytes, long maxBytes) {
        long usedPercent = RuntimeLogSanitizer.percent(usedBytes, maxBytes);
        int threshold = properties.getJvm().getDirectMemoryThresholdPercent();
        if (maxBytes <= 0 || usedPercent < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "jvm_direct_memory_pressure", "threshold", "jvm direct memory pressure")
                .field("jvm.memory.area", "direct")
                .field("jvm.memory.used.bytes", usedBytes)
                .field("jvm.memory.max.bytes", maxBytes)
                .field("jvm.memory.used.percent", usedPercent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }

    public void logClassLoadingSummary(long loadedClassCount, long unloadedClassCount, long loadedClassDelta) {
        logWriter.info(RuntimeLogEvent.builder("runtime", "jvm_class_loading_summary", "success", "jvm class loading summary")
                .field("jvm.classes.loaded", loadedClassCount)
                .field("jvm.classes.unloaded", unloadedClassCount)
                .field("jvm.classes.loaded.delta", loadedClassDelta)
                .build());
    }
}
