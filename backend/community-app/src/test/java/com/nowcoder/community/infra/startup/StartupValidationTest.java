package com.nowcoder.community.infra.startup;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupValidationTest {

    @Test
    void prodShouldRejectMissingRequiredNacosImportsWhenEnabled() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.nacos.config.required", "true");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NACOS_CONFIG_IMPORT_SHARED")
                .hasMessageContaining("NACOS_CONFIG_IMPORT_SERVICE");
    }

    @Test
    void prodShouldAcceptRequiredNacosImportsWhenConfigured() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.nacos.config.required", "true")
                .withProperty("NACOS_CONFIG_IMPORT_SHARED", "nacos:community-shared.yaml?group=COMMUNITY")
                .withProperty("NACOS_CONFIG_IMPORT_SERVICE", "nacos:community-app.yaml?group=COMMUNITY");

        new StartupValidation().validateOrThrow(environment);
    }

    @Test
    void prodShouldRejectNonDbSocialStorage() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("social.storage", "redis");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social.storage=db");
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.hmac-secret", "01234567890123456789012345678901");
        environment.setActiveProfiles("prod");
        return environment;
    }
}
