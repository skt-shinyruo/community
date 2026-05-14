package com.nowcoder.community.common.observability.data;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlRuntimeLoggerTest {

    @Test
    void logsSlowSqlWithoutSqlParameters() {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.sql-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getSql().setSlowQueryThresholdMs(200);
            SqlRuntimeLogger logger = new SqlRuntimeLogger(capture.writer(), properties);

            assertThat(logger.logSlowQuery("com.nowcoder.UserMapper.selectByEmail", "SELECT * FROM user WHERE email = ?", 199, 12, null)).isFalse();
            assertThat(logger.logSlowQuery("com.nowcoder.UserMapper.selectByEmail", "SELECT * FROM user WHERE email = 'secret@example.com'", 201, 1234, new RuntimeException("timeout"))).isTrue();

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.COMMUNITY_CATEGORY, "database")
                    .containsEntry(RuntimeLogFields.COMMUNITY_ACTION, "sql_slow_query")
                    .containsEntry("db.system", "mysql")
                    .containsEntry("db.operation", "select")
                    .containsEntry("db.mybatis.statement", "com.nowcoder.UserMapper.selectByEmail")
                    .containsEntry("db.rows.bucket", "1000+")
                    .containsEntry("duration.ms", "201")
                    .containsEntry("threshold.ms", "200")
                    .doesNotContainKey("db.statement.params");
            assertThat(capture.appender().list.get(0).getFormattedMessage()).doesNotContain("secret@example.com");
        }
    }
}
