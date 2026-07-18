package com.nowcoder.community.infra.startup;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

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

    @Test
    void prodShouldRejectUnsafeCommunityWebTrustedProxyCidr() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty("community.web.trusted-proxy.cidrs[0]", "0.0.0.0/0");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("community.web.trusted-proxy.cidrs[0]=0.0.0.0/0");
    }

    @Test
    void prodShouldIgnoreGatewayOwnerTrustedProxyConfiguration() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "foreign-gateway-owner",
                Map.of(
                        "SPRING_APPLICATION_NAME", "community-app",
                        "SECURITY_JWT_HMAC_SECRET", "01234567890123456789012345678901",
                        "GATEWAY_TRUSTED_PROXY_ENABLED", "true",
                        "GATEWAY_TRUSTED_PROXY_CIDRS", "0.0.0.0/0"
                )
        ));

        new StartupValidation().validateOrThrow(environment);
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
