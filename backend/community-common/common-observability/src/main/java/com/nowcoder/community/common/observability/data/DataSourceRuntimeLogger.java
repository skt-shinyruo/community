package com.nowcoder.community.common.observability.data;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataSourceRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;
    private final DataSource dataSource;
    private final AtomicBoolean instrumentationSkippedLogged = new AtomicBoolean();

    public DataSourceRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties, DataSource dataSource) {
        this.logWriter = logWriter;
        this.properties = properties;
        this.dataSource = dataSource;
    }

    public RuntimeLogWriter logWriter() {
        return logWriter;
    }

    public RuntimeLoggingProperties properties() {
        return properties;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public boolean logPoolSnapshot() {
        if (dataSource == null) {
            return false;
        }
        try {
            Object poolMxBean = invoke(dataSource, "getHikariPoolMXBean");
            if (poolMxBean == null) {
                return false;
            }
            String poolName = valueOrDefault(invoke(dataSource, "getPoolName"), "unknown");
            return logPoolSnapshot(new PoolSnapshot(
                    poolName,
                    intValue(invoke(poolMxBean, "getActiveConnections")),
                    intValue(invoke(poolMxBean, "getIdleConnections")),
                    intValue(invoke(poolMxBean, "getTotalConnections")),
                    intValue(invoke(poolMxBean, "getThreadsAwaitingConnection"))
            ));
        } catch (RuntimeException ex) {
            if (instrumentationSkippedLogged.compareAndSet(false, true)) {
                logWriter.warn(RuntimeLogEvent.builder("database", "runtime_instrumentation_skipped", "skipped", "runtime instrumentation skipped")
                        .field("instrumentation.action", "hikari_pool_pressure")
                        .field(RuntimeLogFields.ERROR_TYPE, ex.getClass().getName())
                        .field(RuntimeLogFields.ERROR_MESSAGE, sanitize(ex.getMessage()))
                        .build());
            }
            return false;
        }
    }

    public boolean logPoolSnapshot(PoolSnapshot snapshot) {
        int pendingThreshold = properties.getDatasource().getPoolPendingThreshold();
        if (snapshot.pending() < pendingThreshold) {
            return false;
        }
        logWriter.warn(RuntimeLogEvent.builder("database", "hikari_pool_pressure", "threshold", "hikari pool pressure")
                .field("db.system", "mysql")
                .field("db.pool.name", snapshot.name())
                .field("db.pool.active", snapshot.active())
                .field("db.pool.idle", snapshot.idle())
                .field("db.pool.total", snapshot.total())
                .field("db.pool.pending", snapshot.pending())
                .build());
        return true;
    }

    public record PoolSnapshot(String name, int active, int idle, int total, int pending) {
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to inspect datasource method " + methodName, ex);
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String valueOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.replaceAll("[\\r\\n\\t]+", " ");
    }
}
