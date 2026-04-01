package com.nowcoder.community.common.security.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCommonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityCommonAutoConfiguration.class);

    @Test
    void context_shouldFailWhenSecretInvalid() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.hmac-secret=short-secret",
                        "security.jwt.issuer=community-auth"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("security.jwt.hmac-secret");
                });
    }

    @Test
    void context_shouldFailWhenIssuerMissing() {
        contextRunner
                .withPropertyValues("security.jwt.hmac-secret=plan-test-jwt-secret-please-change-123456")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("security.jwt.issuer");
                });
    }

    @Test
    void context_shouldStartWhenJwtConfigurationValid() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.hmac-secret=plan-test-jwt-secret-please-change-123456",
                        "security.jwt.issuer=community-auth"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }
}
