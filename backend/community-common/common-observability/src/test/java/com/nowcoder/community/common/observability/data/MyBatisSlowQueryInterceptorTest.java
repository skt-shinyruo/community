package com.nowcoder.community.common.observability.data;

import com.nowcoder.community.common.observability.logging.RuntimeLogFields;
import com.nowcoder.community.common.observability.logging.RuntimeLogTestSupport;
import com.nowcoder.community.common.observability.logging.RuntimeLoggingProperties;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisSlowQueryInterceptorTest {

    @Test
    void logsSlowQueryWithoutSqlParametersOrRawSql() throws Throwable {
        try (RuntimeLogTestSupport.Capture capture = RuntimeLogTestSupport.capture("test.mybatis-runtime")) {
            RuntimeLoggingProperties properties = new RuntimeLoggingProperties();
            properties.getSql().setSlowQueryThresholdMs(1);
            SqlRuntimeLogger logger = new SqlRuntimeLogger(capture.writer(), properties);
            AtomicLong now = new AtomicLong(0);
            MyBatisSlowQueryInterceptor interceptor = new MyBatisSlowQueryInterceptor(logger, () -> now.getAndAdd(2_000_000));
            Configuration configuration = new Configuration();
            MappedStatement statement = new MappedStatement.Builder(
                    configuration,
                    "com.example.SecretMapper.select",
                    new StaticSqlSource(configuration, "select * from users where password = ?", List.of()),
                    SqlCommandType.SELECT
            ).build();
            Invocation invocation = new Invocation(new Target(), targetMethod(), new Object[]{
                    statement,
                    "secret-token",
                    RowBounds.DEFAULT,
                    (ResultHandler<?>) context -> {
                    }
            });

            assertThat(interceptor.intercept(invocation)).isEqualTo(List.of("a", "b"));

            assertThat(capture.appender().list).hasSize(1);
            assertThat(capture.appender().list.get(0).getMDCPropertyMap())
                    .containsEntry(RuntimeLogFields.EVENT_ACTION, "sql_slow_query")
                    .containsEntry("db.operation", "select")
                    .containsEntry("db.mybatis.statement", "com.example.SecretMapper.select")
                    .containsEntry("db.rows.bucket", "2-10")
                    .doesNotContainEntry("db.statement", "select * from users where password = ?")
                    .doesNotContainValue("secret-token");
        }
    }

    private static Method targetMethod() throws NoSuchMethodException {
        return Target.class.getMethod("query", MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
    }

    public static class Target {
        public Object query(MappedStatement statement, Object parameter, RowBounds rowBounds, ResultHandler<?> resultHandler) {
            return List.of("a", "b");
        }
    }
}
