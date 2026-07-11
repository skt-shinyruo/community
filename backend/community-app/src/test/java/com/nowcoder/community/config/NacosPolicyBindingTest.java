package com.nowcoder.community.config;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.auth.config.RefreshTokenCleanupProperties;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.common.spring.policy.CachePolicyProperties;
import com.nowcoder.community.common.spring.policy.KafkaPolicyProperties;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import com.nowcoder.community.common.web.net.TrustedProxyProperties;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import com.nowcoder.community.notice.application.NoticePolicyProperties;
import com.nowcoder.community.runtime.config.RuntimeConfigProperties;
import com.nowcoder.community.search.application.SearchPolicyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.time.Duration;
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
        assertThat(originGuard.getAllowedOrigins()).contains("http://localhost:12881");
        assertThat(environment.containsProperty("auth.login-rate-limit.enabled")).isTrue();
        assertThat(environment.containsProperty("auth.login-rate-limit.max-failures-per-user")).isTrue();
        assertThat(loginRateLimit.isEnabled()).isTrue();
        assertThat(loginRateLimit.getMaxFailuresPerUser()).isEqualTo(5);
        assertThat(environment.containsProperty("auth.refresh.cleanup.interval-ms")).isTrue();
        assertThat(refreshCleanup.isEnabled()).isTrue();
        assertThat(refreshCleanup.getIntervalMs()).isEqualTo(3_600_000L);
        assertThat(environment.getProperty("auth.password-reset.reset-base-url")).isEqualTo("http://localhost:12881");
        assertThat(environment.getProperty("auth.registration.mail.from")).isEqualTo("no-reply@community.local");
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("mailhog");
        assertThat(environment.getProperty("spring.mail.port", Integer.class)).isEqualTo(1025);
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class)).isFalse();
        assertThat(environment.getProperty("http.idempotency.store")).isEqualTo("DB");
        assertThat(environment.getProperty("growth.business-zone-id")).isEqualTo("Asia/Shanghai");
        assertThat(environment.getProperty("search.index.initialize", Boolean.class)).isTrue();
        assertThat(environment.getProperty("management.health.elasticsearch.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("analytics.ingest.exclude-paths[2]")).isEqualTo("/api/ops/**");
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("10GB");
    }

    @Test
    void bindsSharedSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-shared.yaml");
        Binder binder = Binder.get(environment);
        TrustedProxyProperties trustedProxy = binder.bind("gateway.trusted-proxy", TrustedProxyProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(trustedProxy.isEnabled()).isFalse();
        assertThat(trustedProxy.getCidrs()).isEmpty();
        assertThat(environment.getProperty("community.metrics.basic-auth.username")).isEqualTo("prometheus");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info,prometheus");
    }

    @Test
    void bindsRuntimePolicySeedDataIds() throws Exception {
        assertThat(Binder.get(environmentFrom("community-feature-flags.yaml"))
                .bind("community", FeatureFlagProperties.class)
                .orElseThrow(IllegalStateException::new)
                .getFlags())
                .containsEntry("post-publishing", true)
                .containsEntry("analytics-ingest", false);

        assertThat(Binder.get(environmentFrom("community-degradation.yaml"))
                .bind("community", DegradationProperties.class)
                .orElseThrow(IllegalStateException::new)
                .getModes())
                .containsEntry("search", "strict")
                .containsEntry("analytics", "best-effort");

        CachePolicyProperties cache = Binder.get(environmentFrom("community-cache-policy.yaml"))
                .bind("community.cache", CachePolicyProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(cache.getDefaultTtl()).isEqualTo(Duration.ofSeconds(300));
        assertThat(cache.getNullTtl()).isEqualTo(Duration.ofSeconds(30));

        UploadPolicyProperties upload = Binder.get(environmentFrom("community-upload-policy.yaml"))
                .bind("community.upload", UploadPolicyProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(upload.getMaxFileSize().toGigabytes()).isEqualTo(10);
        assertThat(upload.getAllowedMimeTypes()).contains("image/png", "text/plain", "application/octet-stream");
        assertThat(upload.getAllowedExtensions()).contains("jpg", "pdf", "txt", "bin");

        SearchPolicyProperties search = Binder.get(environmentFrom("community-search-policy.yaml"))
                .bind("search", SearchPolicyProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(search.getIndex().isInitialize()).isTrue();
        assertThat(search.getQuery().getMaxPageSize()).isEqualTo(50);

        NoticePolicyProperties notice = Binder.get(environmentFrom("community-notification-policy.yaml"))
                .bind("notice", NoticePolicyProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(notice.getChannels().isInAppEnabled()).isTrue();
        assertThat(notice.getDigest().getWindow()).isEqualTo(Duration.ofHours(1));

        StandardEnvironment kafkaEnvironment = environmentFrom("community-kafka-policy.yaml");
        KafkaPolicyProperties kafka = Binder.get(kafkaEnvironment)
                .bind("community.kafka-policy", KafkaPolicyProperties.class)
                .orElseThrow(IllegalStateException::new);
        assertThat(kafka.getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(kafka.getProducer().getAcks()).isEqualTo("all");
        assertThat(kafka.getProducer().getMaxInFlightRequests()).isEqualTo(5);
        assertThat(kafka.getProducer().getRequestTimeoutMs()).isEqualTo(3000);
        assertThat(environmentFrom("community-kafka-policy.yaml").getProperty("community.kafka-policy.producer.acks"))
                .isEqualTo("all");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("community.kafka-policy.producer.request-timeout-ms", Integer.class)).isEqualTo(3000);
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("im.kafka.topics.command-private-text")).isEqualTo("im.command.private-text");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("im.kafka.topics.event-user-block-relation-changed"))
                .isEqualTo("im.event.user-block-relation-changed");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("im.kafka.topics.event-private-committed"))
                .isEqualTo("im.event.private-committed");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("im.kafka.topics.event-room-committed"))
                .isEqualTo("im.event.room-committed");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("search.outbox.post-topic")).isEqualTo("projection.search.post");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("im.policy.outbox.topic")).isEqualTo("projection.im.policy");
        assertThat(environmentFrom("community-kafka-policy.yaml")
                .getProperty("user.reward.outbox.comment-topic")).isEqualTo("projection.user.reward.comment");
        assertThat(kafkaEnvironment.containsProperty("content.events.publisher")).isFalse();
        assertThat(kafkaEnvironment.containsProperty("social.events.publisher")).isFalse();
        assertThat(kafkaEnvironment.containsProperty("user.events.publisher")).isFalse();
        assertThat(kafkaEnvironment.getProperty("content.events.outbox-topic")).isEqualTo("eventbus.content");
        assertThat(kafkaEnvironment.getProperty("content.events.kafka-topic")).isEqualTo("content.events");
        assertThat(kafkaEnvironment.getProperty("social.events.outbox-topic")).isEqualTo("eventbus.social");
        assertThat(kafkaEnvironment.getProperty("social.events.kafka-topic")).isEqualTo("social.events");
        assertThat(kafkaEnvironment.getProperty("user.events.outbox-topic")).isEqualTo("eventbus.user");
        assertThat(kafkaEnvironment.getProperty("user.events.kafka-topic")).isEqualTo("user.events");
    }

    @Test
    void bindsFrontendRuntimeSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-frontend-runtime.yaml");
        RuntimeConfigProperties runtime = Binder.get(environment)
                .bind("frontend.runtime", RuntimeConfigProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertThat(runtime.getApiBasePath()).isEqualTo("/api");
        assertThat(runtime.getFeatures()).containsEntry("file-upload", true);
        assertThat(environment.getProperty("frontend.runtime.upload.max-file-size")).isEqualTo("10GB");
        assertThat(environment.getProperty("frontend.runtime.upload.max-request-size")).isEqualTo("10GB");
        assertThat(environment.getProperty("frontend.runtime.upload.allowed-mime-types[0]")).isEqualTo("image/jpeg");
        assertThat(environment.getProperty("frontend.runtime.upload.allowed-extensions[0]")).isEqualTo("jpg");
        assertThat(environment.getProperty("frontend.runtime.upload.avatar-upload-enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("frontend.runtime.upload.media-upload-enabled", Boolean.class)).isTrue();
    }

    @Test
    void bindsWorkProcessingSeedDataId() throws Exception {
        StandardEnvironment environment = environmentFrom("community-work-processing.yaml");

        assertThat(environment.getProperty("content.score.refresh.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("content.score.refresh.batch-size", Integer.class)).isEqualTo(200);
        assertThat(environment.getProperty("content.score.refresh.delay-ms", Long.class)).isEqualTo(30_000L);
        assertThat(environment.getProperty("market.wallet-action.process-batch-size", Integer.class)).isEqualTo(50);
        assertThat(environment.getProperty("market.wallet-action.recovery-batch-size", Integer.class)).isEqualTo(100);
        assertThat(environment.getProperty("market.wallet-action.processing-lease")).isEqualTo("60s");
        assertThat(environment.getProperty("drive.upload.recovery.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("drive.upload.recovery.batch-size", Integer.class)).isEqualTo(100);
        assertThat(environment.getProperty("drive.upload.recovery.stale-seconds", Long.class)).isEqualTo(300L);
        assertThat(environment.getProperty("drive.upload.recovery.delay-ms", Long.class)).isEqualTo(60_000L);
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
