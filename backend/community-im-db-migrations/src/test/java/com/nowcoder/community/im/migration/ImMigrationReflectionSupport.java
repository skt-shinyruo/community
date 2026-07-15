package com.nowcoder.community.im.migration;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ImMigrationReflectionSupport {

    static final String APPLICATION_CLASS =
            "com.nowcoder.community.im.migration.ImMigrationApplication";
    static final String RUNNER_CLASS =
            "com.nowcoder.community.im.migration.ImMigrationRunner";
    static final String CATALOG_CLASS =
            "com.nowcoder.community.im.migration.ImSchemaCatalog";
    static final String VERIFIER_CLASS =
            "com.nowcoder.community.im.migration.ImSchemaVerifier";

    private ImMigrationReflectionSupport() {
    }

    static Class<?> requireClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("missing MIG-02B production contract: " + className, exception);
        }
    }

    static Object newStandardRunner(String jdbcUrl, String username, String password) {
        return invokeStatic(
                requireClass(RUNNER_CLASS),
                "standard",
                new Class<?>[]{String.class, String.class, String.class},
                jdbcUrl,
                username,
                password
        );
    }

    static Object newRunnerForLocations(
            String jdbcUrl,
            String username,
            String password,
            String historyTable,
            String location
    ) {
        return invokeStatic(
                requireClass(RUNNER_CLASS),
                "forLocations",
                new Class<?>[]{String.class, String.class, String.class, String.class, String[].class},
                jdbcUrl,
                username,
                password,
                historyTable,
                new String[]{location}
        );
    }

    static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        return invokeMethod(target.getClass(), target, methodName, parameterTypes, arguments);
    }

    static Object invokeStatic(
            Class<?> owner,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        return invokeMethod(owner, null, methodName, parameterTypes, arguments);
    }

    static String stringConstant(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(owner.getName() + " must declare " + fieldName, exception);
        }
    }

    static int migrationsExecuted(Object migrateResult) {
        try {
            Field field = migrateResult.getClass().getField("migrationsExecuted");
            return ((Number) field.get(migrateResult)).intValue();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("runner migrate() must return Flyway MigrateResult", exception);
        }
    }

    private static Object invokeMethod(
            Class<?> owner,
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            Method method = owner.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            return sneakyThrow(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(owner.getName() + " must declare " + methodName, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }
}
