package com.nowcoder.community.auth.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthStartupValidatorTest {

    @Test
    void validateShouldAcceptSecureProductionConfiguration() {
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(secureCommunityAppEnvironment(), errors);

        assertThat(errors).isEmpty();
    }

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
    void validateShouldNotAllowApplicationNameToBypassAuthChecks() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("spring.application.name", "attacker-controlled")
                .withProperty("auth.registration.code.expose-code", "true");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error ->
                assertThat(error).contains("auth.registration.code.expose-code"));
    }

    @ParameterizedTest
    @MethodSource("invalidRefreshTokenSecurityBounds")
    void validateShouldRejectUnsafeRefreshTokenBounds(String key, String value) {
        MockEnvironment environment = secureCommunityAppEnvironment().withProperty(key, value);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error).contains(key));
    }

    @Test
    void validateShouldRejectRefreshReuseGraceEqualToTokenTtl() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("security.jwt.refresh-token-ttl-seconds", "300")
                .withProperty("security.jwt.refresh-reuse-grace-seconds", "300");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("security.jwt.refresh-reuse-grace-seconds")
                .contains("security.jwt.refresh-token-ttl-seconds")
                .contains("小于"));
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

    @Test
    void validateShouldRejectDisabledOrFailOpenOriginGuard() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("gateway.origin-guard.enabled", "false")
                .withProperty("gateway.origin-guard.fail-open-when-allowlist-empty", "true");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("gateway.origin-guard.enabled"));
        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("gateway.origin-guard.fail-open-when-allowlist-empty")
                .contains("fail-closed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ",",
            "https://community.example/reset",
            "https://user@community.example",
            "ftp://community.example",
            "not an origin"
    })
    void validateShouldRejectMalformedOriginAllowlist(String origins) {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("gateway.origin-guard.allowed-origins", origins);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("gateway.origin-guard.allowed-origins"));
    }

    @Test
    void validateShouldRejectAuthenticatedSmtpWithoutCredentialsOrEncryption() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("spring.mail.properties.mail.smtp.auth", "true")
                .withProperty("spring.mail.username", " ")
                .withProperty("spring.mail.password", " ")
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "false")
                .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "false");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error).contains("spring.mail.username"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("spring.mail.password"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("STARTTLS"));
    }

    @Test
    void validateShouldRejectPlaintextOrDowngradableSmtpWithoutAuthentication() {
        MockEnvironment plaintext = secureCommunityAppEnvironment()
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "false")
                .withProperty("spring.mail.properties.mail.smtp.starttls.required", "false")
                .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "false");
        MockEnvironment opportunistic = secureCommunityAppEnvironment()
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
                .withProperty("spring.mail.properties.mail.smtp.starttls.required", "false")
                .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "false");

        List<String> plaintextErrors = new ArrayList<>();
        List<String> opportunisticErrors = new ArrayList<>();
        new AuthStartupValidator().validate(plaintext, plaintextErrors);
        new AuthStartupValidator().validate(opportunistic, opportunisticErrors);

        assertThat(plaintextErrors).anySatisfy(error -> assertThat(error).contains("生产 SMTP"));
        assertThat(opportunisticErrors).anySatisfy(error -> assertThat(error).contains("生产 SMTP"));
    }

    @Test
    void validateShouldRejectDevelopmentMailSentinels() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("spring.mail.host", "mailhog")
                .withProperty("auth.registration.mail.from", "no-reply@community.local");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error).contains("spring.mail.host").contains("开发"));
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("auth.registration.mail.from").contains(".local"));
    }

    @ParameterizedTest
    @MethodSource("invalidAbusePreventionQuotas")
    void validateShouldRejectDisabledOrUnboundedAbusePreventionQuota(String key, String value) {
        MockEnvironment environment = secureCommunityAppEnvironment().withProperty(key, value);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error).contains(key));
    }

    @Test
    void validateShouldRejectMissingPasswordResetIdentifierSecret() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.identifier-hmac-secret", " ");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.password-reset.identifier-hmac-secret")
                .contains("AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET"));
    }

    @Test
    void validateShouldRejectPasswordResetIdentifierSecretReusedFromServiceJwt() {
        String reusedSecret = "service-jwt-secret-at-least-32-bytes";
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("security.jwt.service-hmac-secret", reusedSecret)
                .withProperty("auth.password-reset.identifier-hmac-secret", reusedSecret);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.password-reset.identifier-hmac-secret")
                .contains("security.jwt.service-hmac-secret")
                .contains("不同密钥"));
    }

    @Test
    void validateShouldRejectShortPasswordResetIdentifierSecret() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.identifier-hmac-secret", "too-short");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.password-reset.identifier-hmac-secret")
                .contains("长度不足"));
    }

    @Test
    void validateShouldRejectRepositoryExamplePasswordResetSecret() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.identifier-hmac-secret",
                        "dev-local-password-reset-identifier-hmac-secret-change-before-sharing-20260804");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.password-reset.identifier-hmac-secret")
                .contains("开发占位密钥"));
    }

    @Test
    void validateShouldInspectIndexedPasswordResetRotationSecrets() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.previous-identifier-hmac-secrets[0]", "short");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("previous-identifier-hmac-secrets")
                .contains("长度不足"));
    }

    @Test
    void validateShouldRejectUnboundedSmtpTimeouts() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("spring.mail.properties.mail.smtp.timeout", "0");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("spring.mail.properties.mail.smtp.timeout")
                .contains("1..30000"));
    }

    @Test
    void validateShouldRejectRegistrationLeaseShorterThanSmtpBudget() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.registration.code.operation-lease-seconds", "60")
                .withProperty("spring.mail.properties.mail.smtp.connectiontimeout", "20000")
                .withProperty("spring.mail.properties.mail.smtp.timeout", "20000")
                .withProperty("spring.mail.properties.mail.smtp.writetimeout", "20000");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.registration.code.operation-lease-seconds")
                .contains("SMTP"));
    }

    @ParameterizedTest
    @MethodSource("invalidLoginAndRegistrationSecurityBounds")
    void validateShouldRejectUnsafeLoginOrRegistrationBounds(String key, String value) {
        MockEnvironment environment = secureCommunityAppEnvironment().withProperty(key, value);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error).contains(key));
    }

    @Test
    void validateShouldAcceptPasswordCheckLeaseAtRuntimeMinimum() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.login-rate-limit.password-check-lease-seconds", "30");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).isEmpty();
    }

    @Test
    void validateShouldRejectRedisTimeoutThatCanOutliveTheLoginLeaseRenewalInterval() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.login-rate-limit.password-check-lease-seconds", "30")
                .withProperty("spring.data.redis.timeout", "8s");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("spring.data.redis.timeout")
                .contains("续租间隔"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0ms", "6s", "invalid"})
    void validateShouldRejectUnboundedOrMalformedRedisConnectTimeout(String timeout) {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("spring.data.redis.connect-timeout", timeout);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("spring.data.redis.connect-timeout"));
    }

    @Test
    void validateShouldRejectCaptchaThresholdAboveFailureLimit() {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.login-rate-limit.max-failures-per-user", "3")
                .withProperty("auth.login-rate-limit.captcha-required-failures-per-user", "4");
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("captcha-required-failures-per-user")
                .contains("不得大于"));
    }

    @Test
    void validateShouldRejectDuplicatePasswordResetRotationSecrets() {
        String current = "password-reset-identifier-secret-at-least-32-bytes";
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.identifier-hmac-secret", current)
                .withProperty("auth.password-reset.previous-identifier-hmac-secrets", current);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("previous-identifier-hmac-secrets")
                .contains("当前密钥"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://community.example",
            "//community.example",
            "https://user@community.example",
            "https://community.example/reset?campaign=mail",
            "https://community.example/reset#fragment",
            "not a uri"
    })
    void validateShouldRejectUnsafePasswordResetBaseUrl(String baseUrl) {
        MockEnvironment environment = secureCommunityAppEnvironment()
                .withProperty("auth.password-reset.reset-base-url", baseUrl);
        List<String> errors = new ArrayList<>();

        new AuthStartupValidator().validate(environment, errors);

        assertThat(errors).anySatisfy(error -> assertThat(error)
                .contains("auth.password-reset.reset-base-url")
                .contains("HTTPS URL")
                .doesNotContain(baseUrl));
    }

    private MockEnvironment secureCommunityAppEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.application.name", "community-app")
                .withProperty("security.jwt.refresh-cookie-secure", "true")
                .withProperty("security.jwt.refresh-cookie-same-site", "Lax")
                .withProperty("security.jwt.refresh-token-ttl-seconds", "604800")
                .withProperty("security.jwt.refresh-reuse-grace-seconds", "10")
                .withProperty("security.jwt.service-hmac-secret", "service-jwt-secret-at-least-32-bytes")
                .withProperty("auth.password-reset.reset-base-url", "https://community.example")
                .withProperty(
                        "auth.password-reset.identifier-hmac-secret",
                        "password-reset-identifier-secret-at-least-32-bytes"
                )
                .withProperty(
                        "auth.password-reset.quota-hmac-secret",
                        "password-reset-stable-quota-secret-at-least-32-bytes"
                )
                .withProperty("auth.registration.mail.enabled", "true")
                .withProperty("auth.registration.mail.from", "no-reply@community.example")
                .withProperty("spring.mail.host", "smtp.example.com")
                .withProperty("spring.mail.port", "587")
                .withProperty("spring.mail.properties.mail.smtp.auth", "false")
                .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
                .withProperty("spring.mail.properties.mail.smtp.starttls.required", "true")
                .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "false")
                .withProperty("spring.mail.properties.mail.smtp.connectiontimeout", "10000")
                .withProperty("spring.mail.properties.mail.smtp.timeout", "10000")
                .withProperty("spring.mail.properties.mail.smtp.writetimeout", "10000")
                .withProperty("auth.registration.code.operation-lease-seconds", "120")
                .withProperty("auth.registration.code.ttl-seconds", "600")
                .withProperty("auth.registration.code.max-failures", "3")
                .withProperty("auth.registration.code.resend-cooldown-seconds", "60")
                .withProperty("auth.registration.draft.ttl-seconds", "1800")
                .withProperty("auth.login-rate-limit.enabled", "true")
                .withProperty("auth.login-rate-limit.window-seconds", "60")
                .withProperty("auth.login-rate-limit.max-failures-per-ip", "20")
                .withProperty("auth.login-rate-limit.max-failures-per-user", "5")
                .withProperty("auth.login-rate-limit.captcha-required-failures-per-ip", "5")
                .withProperty("auth.login-rate-limit.captcha-required-failures-per-user", "2")
                .withProperty("auth.login-rate-limit.password-check-lease-seconds", "120")
                .withProperty("spring.data.redis.connect-timeout", "2s")
                .withProperty("spring.data.redis.timeout", "2s")
                .withProperty("gateway.origin-guard.enabled", "true")
                .withProperty("gateway.origin-guard.fail-open-when-allowlist-empty", "false")
                .withProperty("gateway.origin-guard.allowed-origins", "https://community.example")
                .withProperty("auth.password-reset.ttl-seconds", "600")
                .withProperty("auth.password-reset.request-window-seconds", "3600")
                .withProperty("auth.password-reset.max-requests-per-email", "3")
                .withProperty("auth.password-reset.max-requests-per-ip", "20")
                .withProperty("auth.captcha.ttl-seconds", "60")
                .withProperty("auth.captcha.max-failures", "3")
                .withProperty("auth.captcha.max-issue-requests-per-ip", "10")
                .withProperty("auth.registration.request-limit.window-seconds", "3600")
                .withProperty("auth.registration.request-limit.max-requests-per-username", "3")
                .withProperty("auth.registration.request-limit.max-requests-per-email", "3")
                .withProperty("auth.registration.request-limit.max-requests-per-ip", "10")
                .withProperty("auth.registration.resend-limit.window-seconds", "3600")
                .withProperty("auth.registration.resend-limit.max-requests-per-registration", "5")
                .withProperty("auth.registration.resend-limit.max-requests-per-email", "5")
                .withProperty("auth.registration.resend-limit.max-requests-per-ip", "20");
    }

    private static Stream<Arguments> invalidRefreshTokenSecurityBounds() {
        return Stream.of(
                Arguments.of("security.jwt.refresh-token-ttl-seconds", "299"),
                Arguments.of("security.jwt.refresh-token-ttl-seconds", "2592001"),
                Arguments.of("security.jwt.refresh-reuse-grace-seconds", "-1"),
                Arguments.of("security.jwt.refresh-reuse-grace-seconds", "301"),
                Arguments.of("security.jwt.refresh-reuse-grace-seconds", "604800")
        );
    }

    private static Stream<Arguments> invalidAbusePreventionQuotas() {
        return Stream.of(
                Arguments.of("auth.password-reset.ttl-seconds", "0"),
                Arguments.of("auth.password-reset.request-window-seconds", "0"),
                Arguments.of("auth.password-reset.max-requests-per-email", "0"),
                Arguments.of("auth.password-reset.max-requests-per-ip", "-1"),
                Arguments.of("auth.captcha.ttl-seconds", "0"),
                Arguments.of("auth.captcha.max-failures", "0"),
                Arguments.of("auth.captcha.max-issue-requests-per-ip", "0"),
                Arguments.of("auth.registration.request-limit.window-seconds", "0"),
                Arguments.of("auth.registration.request-limit.max-requests-per-username", "0"),
                Arguments.of("auth.registration.request-limit.max-requests-per-email", "0"),
                Arguments.of("auth.registration.request-limit.max-requests-per-ip", "0"),
                Arguments.of("auth.registration.resend-limit.window-seconds", "0"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-registration", "0"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-email", "0"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-ip", "0"),
                Arguments.of("auth.registration.resend-limit.window-seconds", "604801"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-registration", "101"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-email", "101"),
                Arguments.of("auth.registration.resend-limit.max-requests-per-ip", "10001")
        );
    }

    private static Stream<Arguments> invalidLoginAndRegistrationSecurityBounds() {
        return Stream.of(
                Arguments.of("auth.login-rate-limit.enabled", "false"),
                Arguments.of("auth.login-rate-limit.window-seconds", "0"),
                Arguments.of("auth.login-rate-limit.max-failures-per-ip", "1001"),
                Arguments.of("auth.login-rate-limit.max-failures-per-user", "51"),
                Arguments.of("auth.login-rate-limit.password-check-lease-seconds", "0"),
                Arguments.of("auth.login-rate-limit.password-check-lease-seconds", "29"),
                Arguments.of("auth.registration.code.ttl-seconds", "86400"),
                Arguments.of("auth.registration.code.max-failures", "1000000"),
                Arguments.of("auth.registration.code.resend-cooldown-seconds", "0"),
                Arguments.of("auth.registration.draft.ttl-seconds", "2147483647"),
                Arguments.of("auth.registration.code.operation-lease-seconds", "601")
        );
    }

}
