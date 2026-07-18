package com.nowcoder.community.infra.startup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0/0",
            "::/0",
            "10.0.0.1/0",
            "0.0.0.0/00",
            "10.0.0.1/0000000000000000000000000000000000000000",
            "2001:db8::1/0"
    })
    void prodShouldRejectEquivalentUniversalCommunityWebTrustedProxyCidrs(String cidr) {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty("community.web.trusted-proxy.cidrs[0]", cidr);

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("community.web.trusted-proxy.cidrs[0]")
                .hasMessageContaining("禁止使用全量信任 CIDR")
                .hasMessageNotContaining("community.web.trusted-proxy.cidrs[0]=")
                .hasMessageNotContaining(cidr);
    }

    @Test
    void prodShouldAcceptClusterCommaSeparatedTrustedProxyCidrs() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty(
                        "community.web.trusted-proxy.cidrs",
                        "172.31.0.20/32,172.31.0.21/32,172.31.0.22/32"
                );

        assertThatCode(() -> new StartupValidation().validateOrThrow(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void prodShouldTrimTrustedProxyCidrListItemsBeforeValidation() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty(
                        "community.web.trusted-proxy.cidrs[0]",
                        " 172.31.0.20/32 "
                );

        assertThatCode(() -> new StartupValidation().validateOrThrow(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void prodShouldRejectBlankTrustedProxyCidrListItems() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty("community.web.trusted-proxy.cidrs[0]", "172.31.0.20/32")
                .withProperty("community.web.trusted-proxy.cidrs[1]", "   ");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("community.web.trusted-proxy.cidrs[1]")
                .hasMessageContaining("为空");
    }

    @Test
    void prodShouldRejectHostnameTrustedProxyCidrWithoutLeakingConfigurationValues() {
        MockEnvironment environment = prodEnvironment()
                .withProperty("community.web.trusted-proxy.enabled", "true")
                .withProperty("community.web.trusted-proxy.cidrs", "proxy.internal/24")
                .withProperty("unrelated.api.secret", "do-not-log-this-secret");

        assertThatThrownBy(() -> new StartupValidation().validateOrThrow(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("community.web.trusted-proxy.cidrs[0]")
                .hasMessageContaining("IPv4/IPv6 literal CIDR")
                .hasMessageNotContaining("proxy.internal")
                .hasMessageNotContaining("do-not-log-this-secret");
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
