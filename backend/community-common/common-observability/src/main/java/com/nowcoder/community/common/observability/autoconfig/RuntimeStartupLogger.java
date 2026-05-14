package com.nowcoder.community.common.observability.autoconfig;

import com.nowcoder.community.common.observability.jvm.JvmRuntimeLogger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

public class RuntimeStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final JvmRuntimeLogger jvmRuntimeLogger;

    public RuntimeStartupLogger(JvmRuntimeLogger jvmRuntimeLogger) {
        this.jvmRuntimeLogger = jvmRuntimeLogger;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logStartupSummary();
    }

    public void logStartupSummary() {
        jvmRuntimeLogger.logStartupSummary();
    }
}
