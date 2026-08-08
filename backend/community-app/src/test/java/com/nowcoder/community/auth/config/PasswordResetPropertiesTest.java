package com.nowcoder.community.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordResetPropertiesConfiguration.class);

    @Test
    void startupShouldRejectMissingIdentifierHmacSecret() {
        contextRunner
                .withPropertyValues(
                        "auth.password-reset.quota-hmac-secret=password-reset-quota-secret-at-least-32-bytes"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("auth.password-reset")
                            .hasMessageContaining("identifierHmacSecret");
                });
    }

    @Test
    void startupShouldRejectMissingStableQuotaHmacSecret() {
        contextRunner
                .withPropertyValues(
                        "auth.password-reset.identifier-hmac-secret=password-reset-identifier-secret-at-least-32-bytes"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("auth.password-reset")
                            .hasMessageContaining("quotaHmacSecret");
                });
    }

    @Test
    void startupShouldBindDedicatedIdentifierHmacSecret() {
        contextRunner
                .withPropertyValues(
                        "auth.password-reset.identifier-hmac-secret=password-reset-identifier-secret-at-least-32-bytes",
                        "auth.password-reset.quota-hmac-secret=password-reset-quota-secret-at-least-32-bytes",
                        "auth.password-reset.previous-identifier-hmac-secrets[0]=previous-password-reset-secret-at-least-32-bytes"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PasswordResetProperties properties = context.getBean(PasswordResetProperties.class);
                    assertThat(properties.getIdentifierHmacSecret())
                            .isEqualTo("password-reset-identifier-secret-at-least-32-bytes");
                    assertThat(properties.getQuotaHmacSecret())
                            .isEqualTo("password-reset-quota-secret-at-least-32-bytes");
                    assertThat(properties.getPreviousIdentifierHmacSecrets())
                            .containsExactly("previous-password-reset-secret-at-least-32-bytes");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordResetProperties.class)
    static class PasswordResetPropertiesConfiguration {
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
