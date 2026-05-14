package com.nowcoder.community.common.observability.data;

import com.nowcoder.community.common.observability.logging.RuntimeLogEvent;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogSanitizer;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;

public class SqlRuntimeLogger {

    private final RuntimeLogWriter logWriter;
    private final RuntimeLoggingProperties properties;

    public SqlRuntimeLogger(RuntimeLogWriter logWriter, RuntimeLoggingProperties properties) {
        this.logWriter = logWriter;
        this.properties = properties;
    }

    public boolean logSlowQuery(String statementId, String sql, long durationMs, long rowCount, Throwable throwable) {
        long threshold = properties.getSql().getSlowQueryThresholdMs();
        if (durationMs < threshold) {
            return false;
        }
        RuntimeLogEvent.Builder builder = RuntimeLogEvent.builder("database", "sql_slow_query", throwable == null ? "threshold" : "failure", "sql slow query")
                .field("db.system", "mysql")
                .field("db.operation", RuntimeLogSanitizer.sqlOperation(sql))
                .field("db.mybatis.statement", RuntimeLogSanitizer.text(statementId))
                .field("db.rows.bucket", RuntimeLogSanitizer.rowBucket(rowCount))
                .field(RuntimeLogFields.DURATION_MS, durationMs)
                .field(RuntimeLogFields.THRESHOLD_MS, threshold);
        if (throwable != null) {
            builder.field(RuntimeLogFields.ERROR_TYPE, throwable.getClass().getName());
        }
        logWriter.warn(builder.build());
        return true;
    }
}
