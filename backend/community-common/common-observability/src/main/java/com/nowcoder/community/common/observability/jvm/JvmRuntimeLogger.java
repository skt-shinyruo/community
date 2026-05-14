package com.nowcoder.community.common.observability.jvm;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.Charset;
import java.time.ZoneId;

public class JvmRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public JvmRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public RuntimeLogWriter logWriter() {
        return logWriter;
    }

    public RuntimeLoggingProperties properties() {
        return properties;
    }

    public void logStartupSummary() {
        try {
            Runtime runtime = Runtime.getRuntime();
            logWriter.info(RuntimeLogEvent.builder("runtime", "jvm_startup", "success", "jvm startup summary")
                    .field("jvm.version", System.getProperty("java.version"))
                    .field("jvm.vendor", System.getProperty("java.vendor"))
                    .field("jvm.available.processors", runtime.availableProcessors())
                    .field("jvm.heap.max.bytes", runtime.maxMemory())
                    .field("process.timezone", ZoneId.systemDefault().getId())
                    .field("process.charset", Charset.defaultCharset().name())
                    .build());
        } catch (RuntimeException ex) {
            logInstrumentationSkipped("jvm_startup", ex);
        }
    }

    public void logMemoryPressureSnapshot() {
        try {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            logMemoryPressure("heap", memoryMXBean.getHeapMemoryUsage().getUsed(), memoryMXBean.getHeapMemoryUsage().getMax());
            logMemoryPressure("non_heap", memoryMXBean.getNonHeapMemoryUsage().getUsed(), memoryMXBean.getNonHeapMemoryUsage().getMax());
        } catch (RuntimeException ex) {
            logInstrumentationSkipped("jvm_memory_pressure", ex);
        }
    }

    public boolean logMemoryPressure(String area, long usedBytes, long maxBytes) {
        if (maxBytes <= 0) {
            return false;
        }
        long usedPercent = usedBytes * 100 / maxBytes;
        int thresholdPercent = properties.getJvm().getMemoryThresholdPercent();
        if (usedPercent < thresholdPercent) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "jvm_memory_pressure", "threshold", "jvm memory pressure")
                .field("jvm.memory.area", area)
                .field("jvm.memory.used.bytes", usedBytes)
                .field("jvm.memory.max.bytes", maxBytes)
                .field("jvm.memory.used.percent", usedPercent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, thresholdPercent)
                .build());
        return true;
    }

    private void logInstrumentationSkipped(String action, RuntimeException ex) {
        logWriter.warn(RuntimeLogEvent.builder("runtime", "runtime_instrumentation_skipped", "skipped", "runtime instrumentation skipped")
                .field("instrumentation.action", action)
                .field(RuntimeLogFields.ERROR_TYPE, ex.getClass().getName())
                .field(RuntimeLogFields.ERROR_MESSAGE, sanitize(ex.getMessage()))
                .build());
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.replaceAll("[\\r\\n\\t]+", " ");
    }
}
