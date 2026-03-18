package com.nowcoder.community.im.core.security;

import com.nowcoder.community.im.core.ImCoreApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreSecurityConfigTest {

    private static final String DEFAULT_PLACEHOLDER_SECRET = "dev-secret-please-change-at-least-32bytes";
    private static final String VALID_SECRET = "im-core-test-jwt-secret-at-least-32b";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(ImCoreApplication.class)
            .withPropertyValues(
                    "server.port=0",
                    "spring.datasource.url=jdbc:h2:mem:im_core_security;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.sql.init.mode=always",
                    "spring.kafka.bootstrap-servers=localhost:0",
                    "spring.kafka.listener.auto-startup=false",
                    "im.room-member-change.publisher=noop"
            );

    @Test
    void applicationConfigShouldNotShipPlaceholderJwtFallback() {
        assertThat(loadMainApplicationProperties().getProperty("security.jwt.hmac-secret"))
                .isEqualTo("${JWT_HMAC_SECRET:}");
    }

    @Test
    void startupShouldFailWhenJwtSecretIsBlank() {
        contextRunner
                .withPropertyValues("security.jwt.hmac-secret=   ")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("security.jwt.hmac-secret");
                });
    }

    @Test
    void startupShouldFailWhenJwtSecretUsesKnownPlaceholder() {
        contextRunner
                .withPropertyValues("security.jwt.hmac-secret=" + DEFAULT_PLACEHOLDER_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("security.jwt.hmac-secret");
                });
    }

    @Test
    void startupShouldAllowExplicitNonPlaceholderJwtSecret() {
        contextRunner
                .withPropertyValues("security.jwt.hmac-secret=" + VALID_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static Properties loadMainApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        return factory.getObject();
    }
}
