package com.nowcoder.community.infra.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class XxlJobAutoConfigurationTest {

    private static final String XXL_JOB_EXECUTOR_CLASS = "com.xxl.job.core.executor.impl.XxlJobSpringExecutor";
    private static final String XXL_JOB_AUTO_CONFIGURATION_CLASS =
            "com.nowcoder.community.infra.job.XxlJobAutoConfiguration";
    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(loadAutoConfiguration(XXL_JOB_AUTO_CONFIGURATION_CLASS)));

    @Test
    void disabledShouldNotCreateExecutorBean() {
        contextRunner
                .withPropertyValues("xxl.job.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(getBeanIfPresent(context, XXL_JOB_EXECUTOR_CLASS)).isNull();
                });
    }

    @Test
    void enabledWithoutRequiredAdminAccessTokenAndAppnameShouldFailFast() {
        contextRunner
                .withPropertyValues("xxl.job.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context))
                            .contains("xxl.job.admin.addresses")
                            .contains("xxl.job.admin.accessToken")
                            .contains("xxl.job.executor.appname");
                });
    }

    @Test
    void enabledWithoutAccessTokenShouldFailFast() {
        contextRunner
                .withPropertyValues(
                        "xxl.job.enabled=true",
                        "xxl.job.admin.addresses=http://127.0.0.1:8080/xxl-job-admin",
                        "xxl.job.executor.appname=community-app"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context)).contains("xxl.job.admin.accessToken");
                });
    }

    @Test
    void enabledWithAddressWithoutExplicitPortShouldFailFast() {
        contextRunner
                .withPropertyValues(
                        "xxl.job.enabled=true",
                        "xxl.job.admin.addresses=http://127.0.0.1:8080/xxl-job-admin",
                        "xxl.job.admin.accessToken=test-access-token",
                        "xxl.job.executor.appname=community-app",
                        "xxl.job.executor.address=http://127.0.0.1/"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseMessage(context)).contains("xxl.job.executor.address");
                });
    }

    @Test
    void enabledWithCompleteConfigShouldCreateExecutorBeanAndUseAddressPortAsBindPort() {
        contextRunner
                .withPropertyValues(
                        "xxl.job.enabled=true",
                        "xxl.job.admin.addresses=http://127.0.0.1:8080/xxl-job-admin",
                        "xxl.job.admin.accessToken=test-access-token",
                        "xxl.job.executor.appname=community-app",
                        "xxl.job.executor.address=http://127.0.0.1:19999/"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Object executor = getBeanIfPresent(context, XXL_JOB_EXECUTOR_CLASS);
                    assertThat(executor).isNotNull();
                    assertThat(readField(executor, "address")).isEqualTo("http://127.0.0.1:19999/");
                    assertThat(readField(executor, "accessToken")).isEqualTo("test-access-token");
                    assertThat(readField(executor, "port")).isEqualTo(19999);
                });
    }

    @Test
    void autoConfigurationImportsShouldIncludeXxlJobAutoConfiguration() throws IOException {
        assertThat(readAutoConfigurationImports()).contains(XXL_JOB_AUTO_CONFIGURATION_CLASS);
    }

    private static Object getBeanIfPresent(AssertableApplicationContext context, String className) {
        Class<?> beanType = resolveClass(className);
        if (beanType == null) {
            return null;
        }
        return context.getBeanProvider(beanType).getIfAvailable();
    }

    private static String rootCauseMessage(AssertableApplicationContext context) {
        Throwable failure = context.getStartupFailure();
        Throwable root = failure;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        return root == null ? null : root.getMessage();
    }

    private static String readAutoConfigurationImports() throws IOException {
        try (InputStream inputStream = XxlJobAutoConfigurationTest.class.getClassLoader()
                .getResourceAsStream(AUTO_CONFIGURATION_IMPORTS)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Class<?> loadAutoConfiguration(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing auto-configuration class: " + className, e);
        }
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("Unable to read field: " + fieldName, e);
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }
}
