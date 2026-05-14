package com.nowcoder.community.common.observability.data;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Properties;
import java.util.function.LongSupplier;

@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "update", args = {
                MappedStatement.class, Object.class
        })
})
public class MyBatisSlowQueryInterceptor implements Interceptor {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final SqlRuntimeLogger logger;
    private final LongSupplier nanoTime;

    public MyBatisSlowQueryInterceptor(SqlRuntimeLogger logger) {
        this(logger, System::nanoTime);
    }

    MyBatisSlowQueryInterceptor(SqlRuntimeLogger logger, LongSupplier nanoTime) {
        this.logger = logger;
        this.nanoTime = nanoTime;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement statement = mappedStatement(args);
        Object parameter = args.length > 1 ? args[1] : null;
        String sql = sql(statement, parameter);
        long startedAtNanos = nanoTime.getAsLong();
        try {
            Object result = invocation.proceed();
            logger.logSlowQuery(statementId(statement), sql, elapsedMillis(startedAtNanos), rowCount(result), null);
            return result;
        } catch (Throwable ex) {
            logger.logSlowQuery(statementId(statement), sql, elapsedMillis(startedAtNanos), -1, failure(ex));
            throw ex;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }

    private MappedStatement mappedStatement(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return null;
        }
        return statement;
    }

    private String statementId(MappedStatement statement) {
        return statement == null ? "-" : statement.getId();
    }

    private String sql(MappedStatement statement, Object parameter) {
        if (statement == null) {
            return "";
        }
        try {
            BoundSql boundSql = statement.getBoundSql(parameter);
            return boundSql == null ? "" : boundSql.getSql();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (nanoTime.getAsLong() - startedAtNanos) / NANOS_PER_MILLI);
    }

    private long rowCount(Object result) {
        if (result == null) {
            return 0;
        }
        if (result instanceof Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Number number) {
            return number.longValue();
        }
        if (result.getClass().isArray()) {
            return Array.getLength(result);
        }
        return 1;
    }

    private Throwable failure(Throwable ex) {
        if (ex instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getTargetException() != null) {
            return invocationTargetException.getTargetException();
        }
        return ex;
    }
}
