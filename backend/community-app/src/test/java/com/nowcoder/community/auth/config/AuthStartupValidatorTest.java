package com.nowcoder.community.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStartupValidatorTest {

    @Test
    void validateShouldRejectExposedRegistrationCodeForCommunityApp() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.registration.code.expose-code", "true");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error ->
                assertThat(error).contains("auth.registration.code.expose-code"));
    }

    @Test
    void validateShouldRejectEnabledOriginGuardWithoutAllowlistWhenFailClosed() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("gateway.origin-guard.enabled", "true")
                .withProperty("gateway.origin-guard.fail-open-when-allowlist-empty", "false")
                .withProperty("gateway.origin-guard.allowed-origins", " ");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error ->
                assertThat(error).contains("gateway.origin-guard.allowed-origins"));
    }

    private MockEnvironment secureCommunityAppEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.refresh-cookie-secure", "true")
                .withProperty("security.jwt.refresh-cookie-same-site", "Lax")
                .withProperty("auth.password-reset.reset-base-url", "https://community.example")
                .withProperty("auth.registration.mail.enabled", "true")
                .withProperty("spring.mail.host", "smtp.example.com");
    }
}
