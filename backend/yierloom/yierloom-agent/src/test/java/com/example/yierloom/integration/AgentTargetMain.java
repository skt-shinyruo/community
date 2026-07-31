package com.example.yierloom.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ExchangeFunction;

public final class AgentTargetMain {
    private static final String HTTP_HOST = "private-host.yierloom.invalid";
    private static final String HTTP_QUERY = "query-secret-yierloom";
    private static final String SQL_LITERAL = "sql-secret-yierloom";
    private static final String REDIS_KEY = "redis-secret-yierloom:key";
    private static final String REDIS_VALUE = "redis-value-secret-yierloom";
    private static final String KAFKA_TOPIC = "kafka-secret-yierloom";
    private static final String KAFKA_PAYLOAD = "kafka-payload-secret-yierloom";
    private static final long VISIBILITY_RATE_REFILL_MILLIS = 1_100;
    private static final long SUMMARY_WAIT_MILLIS = 500;

    private AgentTargetMain() {
    }

    public static void main(String[] arguments) throws Exception {
        boolean visibilityOnly = Arrays.asList(arguments).contains("visibility");
        if (visibilityOnly) {
            runCustomTarget();
            Thread.sleep(VISIBILITY_RATE_REFILL_MILLIS);
            runSystemTarget(false);
        } else {
            runSystemTarget(true);
            runHttp();
            runRedis();
            runKafka();
            runJdbc();
            runCustomTarget();
        }

        Thread.sleep(SUMMARY_WAIT_MILLIS);
        System.out.println("YIERLOOM_THREAD_COUNT=" + yierLoomThreadCount());
        System.out.println("TARGET_COMPLETED");
    }

    private static void runSystemTarget(boolean exerciseAllMethods) throws Exception {
        AgentTargetService service = new AgentTargetService();
        System.out.println("FAST_RESULT=" + service.fast());
        if (exerciseAllMethods) {
            System.out.println("SLOW_RESULT=" + service.slow());
            printTargetException(service);
        }
        System.out.println("SYSTEM_TARGET_LOADER="
                + loaderName(AgentTargetService.class.getClassLoader(), null));
        System.out.println("SYSTEM_HELPER_LOADER=" + service.helperLoaderName());
        System.out.println("SYSTEM_API_LOADER=" + service.apiLoaderName());
    }

    private static void printTargetException(AgentTargetService service) {
        IllegalStateException expected = service.targetBoom();
        try {
            service.throwsTargetBoom();
            throw new AssertionError("throwsTargetBoom returned normally");
        } catch (Throwable failure) {
            if (failure.getClass() != IllegalStateException.class
                    || !"target-boom".equals(failure.getMessage())) {
                throw new AssertionError("target exception changed", failure);
            }
            System.out.println("EXCEPTION_TYPE=" + failure.getClass().getName());
            System.out.println("EXCEPTION_MESSAGE=" + failure.getMessage());
            System.out.println("EXCEPTION_SAME_INSTANCE=" + (failure == expected));
        }
    }

    private static void runHttp() {
        URI uri = URI.create("https://" + HTTP_HOST
                + "/diagnostics/check?token=" + HTTP_QUERY + "#private-fragment");
        ExchangeFunction exchange = new ExchangeFunction.Fixture();
        Object result = exchange.exchange(new ExchangeFunction.Request("GET", uri));
        System.out.println("HTTP_RESULT=" + result);
    }

    private static void runRedis() {
        Object result = new RedisTemplate().execute(REDIS_KEY, REDIS_VALUE);
        System.out.println("REDIS_RESULT=" + result);
    }

    private static void runKafka() {
        Object result = new KafkaTemplate().send(KAFKA_TOPIC, KAFKA_PAYLOAD);
        System.out.println("KAFKA_RESULT=" + result);
    }

    private static void runJdbc() throws Exception {
        Class.forName("org.h2.Driver");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:yierloom");
             Statement statement = connection.createStatement()) {
            boolean result = statement.execute("select '" + SQL_LITERAL + "'");
            System.out.println("JDBC_RESULT=" + result);
        }
    }

    private static void runCustomTarget() throws Exception {
        CustomTargetClassLoader loader = new CustomTargetClassLoader();
        Class<?> targetClass = loader.loadTarget();
        Object target = targetClass.getConstructor().newInstance();
        System.out.println("CUSTOM_RESULT=" + invokeString(targetClass, target, "work"));
        System.out.println("CUSTOM_TARGET_LOADER="
                + loaderName(targetClass.getClassLoader(), loader));
        System.out.println("CUSTOM_HELPER_LOADER="
                + invokeString(targetClass, target, "helperLoaderName"));
        System.out.println("CUSTOM_API_LOADER="
                + invokeString(targetClass, target, "apiLoaderName"));
    }

    private static String invokeString(Class<?> type, Object target, String methodName)
            throws Exception {
        Method method = type.getMethod(methodName);
        try {
            return (String) method.invoke(target);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("custom target invocation failed", cause);
        }
    }

    private static String loaderName(ClassLoader loader, ClassLoader customLoader) {
        if (loader == null) {
            return "bootstrap";
        }
        if (loader == customLoader) {
            return "custom";
        }
        return loader == ClassLoader.getSystemClassLoader()
                ? "system"
                : loader.getClass().getName();
    }

    private static long yierLoomThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(name -> name.startsWith("yierloom-"))
                .count();
    }
}
