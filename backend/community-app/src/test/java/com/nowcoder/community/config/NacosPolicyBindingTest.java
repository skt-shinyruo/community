package com.nowcoder.community.config;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NacosPolicyBindingTest {

    @Test
    void bindsCommunityAppSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-app.yaml");
        Binder binder = Binder.get(environment);

        OriginGuardProperties originGuard = binder.bind("gateway.origin-guard", OriginGuardProperties.class)
                .orElseThrow(IllegalStateException::new);
        LoginRateLimitProperties loginRateLimit = binder.bind("auth.login-rate-limit", LoginRateLimitProperties.class)
                .orElseThrow(IllegalStateException::new);
        RefreshTokenCleanupProperties refreshCleanup = binder
                .bind("auth.refresh.cleanup", RefreshTokenCleanupProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(originGuard.isEnabled()).isTrue();
        assertThat(originGuard.isFailOpenWhenAllowlistEmpty()).isFalse();
        assertThat(environment.containsProperty("auth.login-rate-limit.enabled")).isTrue();
        assertThat(environment.containsProperty("auth.login-rate-limit.max-failures-per-user")).isTrue();
        assertThat(loginRateLimit.isEnabled()).isTrue();
        assertThat(loginRateLimit.getMaxFailuresPerUser()).isEqualTo(5);
        assertThat(environment.containsProperty("auth.refresh.cleanup.interval-ms")).isTrue();
        assertThat(refreshCleanup.isEnabled()).isTrue();
        assertThat(refreshCleanup.getIntervalMs()).isEqualTo(3_600_000L);
        assertThat(environment.getProperty("auth.password-reset.reset-base-url")).isEqualTo("");
        assertThat(environment.getProperty("auth.registration.mail.from")).isEqualTo("no-reply@community.local");
        assertThat(environment.getProperty("http.idempotency.store")).isEqualTo("DB");
        assertThat(environment.getProperty("growth.business-zone-id")).isEqualTo("Asia/Shanghai");
    }

    private static StandardEnvironment environmentFrom(String fileName) throws Exception {
        Path path = seedFile(fileName);
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new YamlPropertySourceLoader().load(fileName, new FileSystemResource(path)).get(0));
        return environment;
    }

    private static Path seedFile(String fileName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("deploy/nacos/config").resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Nacos seed file not found: " + fileName);
    }
}
