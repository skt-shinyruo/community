package com.nowcoder.community.common.observability.data;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogWriter;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRuntimeLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("test.datasource-runtime");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
    private final RuntimeLogWriter writer = new RuntimeLogWriter(logger);

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void logsHikariPoolPressureOnlyWhenPendingThresholdIsReached() {
        appender.start();
        logger.addAppender(appender);
        properties.getDatasource().setPoolPendingThreshold(2);
        DataSourceRuntimeLogger runtimeLogger = new DataSourceRuntimeLogger(writer, properties, null);

        assertThat(runtimeLogger.logPoolSnapshot(new DataSourceRuntimeLogger.PoolSnapshot(
                "HikariPool-1", 8, 2, 10, 1
        ))).isFalse();
        assertThat(appender.list).isEmpty();

        assertThat(runtimeLogger.logPoolSnapshot(new DataSourceRuntimeLogger.PoolSnapshot(
                "HikariPool-1", 9, 1, 10, 2
        ))).isTrue();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_CATEGORY, "database")
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "hikari_pool_pressure")
                .containsEntry(RuntimeLogFields.EVENT_OUTCOME, "threshold")
                .containsEntry("db.system", "mysql")
                .containsEntry("db.pool.name", "HikariPool-1")
                .containsEntry("db.pool.active", "9")
                .containsEntry("db.pool.idle", "1")
                .containsEntry("db.pool.total", "10")
                .containsEntry("db.pool.pending", "2");
    }

    @Test
    void inspectsHikariLikeDataSourceByReflection() {
        appender.start();
        logger.addAppender(appender);
        properties.getDatasource().setPoolPendingThreshold(1);
        DataSourceRuntimeLogger runtimeLogger = new DataSourceRuntimeLogger(
                writer,
                properties,
                new FakeHikariDataSource()
        );

        assertThat(runtimeLogger.logPoolSnapshot()).isTrue();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry("db.pool.name", "FakePool")
                .containsEntry("db.pool.active", "5")
                .containsEntry("db.pool.idle", "4")
                .containsEntry("db.pool.total", "9")
                .containsEntry("db.pool.pending", "1");
    }

    @Test
    void logsInstrumentationSkippedOnlyOnceWhenDatasourceCannotBeInspected() {
        appender.start();
        logger.addAppender(appender);
        DataSourceRuntimeLogger runtimeLogger = new DataSourceRuntimeLogger(
                writer,
                properties,
                new UnsupportedDataSource()
        );

        assertThat(runtimeLogger.logPoolSnapshot()).isFalse();
        assertThat(runtimeLogger.logPoolSnapshot()).isFalse();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry(RuntimeLogFields.EVENT_ACTION, "runtime_instrumentation_skipped")
                .containsEntry("instrumentation.action", "hikari_pool_pressure");
    }

    static final class FakeHikariDataSource implements DataSource {

        public String getPoolName() {
            return "FakePool";
        }

        public FakeHikariPoolMXBean getHikariPoolMXBean() {
            return new FakeHikariPoolMXBean();
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    public static final class FakeHikariPoolMXBean {

        public int getActiveConnections() {
            return 5;
        }

        public int getIdleConnections() {
            return 4;
        }

        public int getTotalConnections() {
            return 9;
        }

        public int getThreadsAwaitingConnection() {
            return 1;
        }
    }

    static final class UnsupportedDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not used");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
