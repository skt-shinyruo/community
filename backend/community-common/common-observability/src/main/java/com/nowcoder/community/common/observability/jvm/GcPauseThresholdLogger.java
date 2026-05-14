package com.nowcoder.community.common.observability.jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.springframework.beans.factory.InitializingBean;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class GcPauseThresholdLogger implements InitializingBean {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final Runnable registration;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public GcPauseThresholdLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this(logWriter, properties, null);
    }

    GcPauseThresholdLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties, Runnable registration) {
        this.logWriter = logWriter;
        this.properties = properties;
        this.registration = registration == null ? this::registerJvmNotificationListeners : registration;
    }

    public RuntimeLogWriter logWriter() {
        return logWriter;
    }

    public RuntimeLoggingProperties properties() {
        return properties;
    }

    public boolean logGcPause(String gcName, long pauseMs) {
        long thresholdMs = properties.getJvm().getGcPauseThresholdMs();
        if (pauseMs < thresholdMs) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("runtime", "jvm_gc_pause_threshold", "threshold", "jvm gc pause threshold")
                .field("jvm.gc.name", gcName)
                .field("jvm.gc.pause.ms", pauseMs)
                .field(RuntimeLogFields.THRESHOLD_MS, thresholdMs)
                .build());
        return true;
    }

    @Override
    public void afterPropertiesSet() {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        try {
            registration.run();
        } catch (RuntimeException ex) {
            logWriter.warn(RuntimeLogEvent.builder("runtime", "runtime_instrumentation_skipped", "skipped", "runtime instrumentation skipped")
                    .field("instrumentation.action", "jvm_gc_pause_threshold")
                    .field(RuntimeLogFields.ERROR_TYPE, ex.getClass().getName())
                    .field(RuntimeLogFields.ERROR_MESSAGE, sanitize(ex.getMessage()))
                    .build());
        }
    }

    private void registerJvmNotificationListeners() {
        for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (garbageCollectorMXBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        return;
                    }
                    Object userData = notification.getUserData();
                    if (userData instanceof CompositeData compositeData) {
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(compositeData);
                        logGcPause(info.getGcName(), info.getGcInfo().getDuration());
                    }
                }, null, null);
            }
        }
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.replaceAll("[\\r\\n\\t]+", " ");
    }
}
