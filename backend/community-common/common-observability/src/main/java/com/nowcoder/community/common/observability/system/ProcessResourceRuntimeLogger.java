package com.nowcoder.community.common.observability.system;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public class ProcessResourceRuntimeLogger {

    private static final String ROOT_MOUNT = "/";

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public ProcessResourceRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public void logResourceSnapshots() {
        logFdSnapshot();
        logDiskSnapshot();
        logCpuSnapshot();
    }

    private void logFdSnapshot() {
        try {
            OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
            Long open = invokeLong(operatingSystem, "getOpenFileDescriptorCount");
            Long max = invokeLong(operatingSystem, "getMaxFileDescriptorCount");
            if (open != null && max != null) {
                logFdPressure(open, max);
            }
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect application work.
        }
    }

    private void logDiskSnapshot() {
        try {
            File root = new File(ROOT_MOUNT);
            long total = root.getTotalSpace();
            long usable = root.getUsableSpace();
            if (total > 0) {
                logDiskPressure(ROOT_MOUNT, total - usable, total);
            }
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect application work.
        }
    }

    private void logCpuSnapshot() {
        try {
            OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
            Double cpuLoad = invokeDouble(operatingSystem, "getProcessCpuLoad");
            if (cpuLoad != null && cpuLoad >= 0) {
                logCpuLoad(Math.round(cpuLoad * 100));
            }
        } catch (RuntimeException ignored) {
            // Runtime logging must not affect application work.
        }
    }

    public boolean logFdPressure(long used, long max) {
        long percent = RuntimeLogSanitizer.percent(used, max);
        int threshold = properties.getSystem().getFdUsageThresholdPercent();
        if (max <= 0 || percent < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "process_fd_pressure", "threshold", "process file descriptor pressure")
                .field("process.fd.used", used)
                .field("process.fd.max", max)
                .field("process.fd.used.percent", percent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }

    public boolean logDiskPressure(String mount, long used, long total) {
        long percent = RuntimeLogSanitizer.percent(used, total);
        int threshold = properties.getSystem().getDiskUsageThresholdPercent();
        if (total <= 0 || percent < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "disk_space_pressure", "threshold", "disk space pressure")
                .field("filesystem.mount", RuntimeLogSanitizer.pathOnly(mount))
                .field("disk.used.bytes", used)
                .field("disk.total.bytes", total)
                .field("disk.used.percent", percent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }

    public boolean logCpuLoad(long cpuLoadPercent) {
        int threshold = properties.getSystem().getCpuLoadThresholdPercent();
        if (cpuLoadPercent < threshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "cpu_load_threshold", "threshold", "cpu load threshold")
                .field("process.cpu.load.percent", cpuLoadPercent)
                .field(RuntimeLogFields.THRESHOLD_PERCENT, threshold)
                .build());
        return true;
    }

    private Long invokeLong(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Double invokeDouble(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
