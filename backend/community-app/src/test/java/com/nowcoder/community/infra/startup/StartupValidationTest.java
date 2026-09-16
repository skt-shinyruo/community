package com.nowcoder.community.infra.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "DEPLOYMENT_ENVIRONMENT",
            "deployment.environment",
            "spring.cloud.nacos.discovery.metadata.deployment.environment"
    })
    void productionDeploymentSignalsShouldRunValidationEvenWithDevProfile(String propertyName) {
        MockEnvironment environment = secureEnvironment("dev")
                .withProperty(propertyName, "production")
                .withProperty("community.nacos.config.required", "true");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NACOS_CONFIG_IMPORT_SHARED");
    }

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

    private MockEnvironment prodEnvironment() {
        return secureEnvironment("prod");
    }

    private MockEnvironment secureEnvironment(String profile) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", profile)
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.access-public-key", "public-key")
                .withProperty("security.jwt.access-private-key", "private-key")
                .withProperty("security.jwt.service-hmac-secret", "01234567890123456789012345678901");
        environment.setActiveProfiles(profile);
        return environment;
    }
}
