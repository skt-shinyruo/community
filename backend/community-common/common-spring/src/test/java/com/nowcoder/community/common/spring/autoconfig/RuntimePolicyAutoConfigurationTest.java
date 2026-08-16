package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.common.spring.policy.KafkaPolicyDecisions;
import com.nowcoder.community.common.spring.policy.KafkaPolicyProperties;
import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePolicyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimePolicyAutoConfiguration.class));

    @Test
    void createsDecisionBeansWithSeedStyleProperties() {
        contextRunner
                .withPropertyValues(
                        "community.features.post-publishing=true",
                        "community.upload.max-file-size=10GB",
                        "community.upload.max-request-size=12GB",
                        "community.upload.allowed-mime-types[0]=image/png",
                        "community.kafka-policy.retry.max-attempts=3",
                        "community.kafka-policy.retry.base-backoff=1s",
                        "community.kafka-policy.dlq.enabled=true",
                        "community.kafka-policy.producer.acks=all",
                        "community.kafka-policy.producer.enable-idempotence=true",
                        "community.kafka-policy.producer.request-timeout-ms=3000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureFlagDecisions.class);
                    assertThat(context).hasSingleBean(UploadPolicyProperties.class);
                    assertThat(context).hasSingleBean(KafkaPolicyProperties.class);
                    assertThat(context).hasSingleBean(UploadPolicyDecisions.class);
                    assertThat(context).hasSingleBean(KafkaPolicyDecisions.class);
                    assertThat(context.getBean(FeatureFlagDecisions.class).enabled("post-publishing")).isTrue();
                    assertThat(context.getBean(UploadPolicyProperties.class).getAllowedMimeTypes()).containsExactly("image/png");
                    assertThat(context.getBean(KafkaPolicyProperties.class).getRetry().getMaxAttempts()).isEqualTo(3);
                    assertThat(context.getBean(KafkaPolicyProperties.class).getDlq().isEnabled()).isTrue();
                    assertThat(context.getBean(KafkaPolicyProperties.class).getProducer().getAcks()).isEqualTo("all");
                    assertThat(context.getBean(KafkaPolicyProperties.class).getProducer().isEnableIdempotence()).isTrue();
                    assertThat(context.getBean(KafkaPolicyProperties.class).getProducer().getRequestTimeoutMs()).isEqualTo(3000);
                    assertThat(context.getBean(UploadPolicyDecisions.class).allowsMimeType("image/png")).isTrue();
                    assertThat(context.getBean(UploadPolicyDecisions.class).maxFileSizeBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
                    assertThat(context.getBean(UploadPolicyDecisions.class).maxRequestSizeBytes()).isEqualTo(12L * 1024 * 1024 * 1024);
                    assertThat(context.getBean(KafkaPolicyDecisions.class).producerIdempotenceEnabled()).isTrue();
                });
    }

    @Test
    void backsOffWhenFeatureFlagDecisionsAlreadyExist() {
        contextRunner
                .withUserConfiguration(CustomDecisionConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureFlagDecisions.class);
                    assertThat(context.getBean(FeatureFlagDecisions.class).enabled("custom")).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDecisionConfiguration {

        @Bean
        FeatureFlagDecisions featureFlagDecisions() {
            FeatureFlagProperties properties = new FeatureFlagProperties();
            properties.getFlags().put("custom", true);
            return new FeatureFlagDecisions(properties);
        }
    }
}
