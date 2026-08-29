package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.policy.KafkaPolicyProperties;
import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimePolicyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimePolicyAutoConfiguration.class));

    @Test
    void createsPolicyBeansWithSeedStyleProperties() {
        contextRunner
                .withPropertyValues(
                        "community.upload.max-file-size=10GB",
                        "community.upload.allowed-mime-types[0]=image/png",
                        "community.kafka-policy.retry.max-attempts=3",
                        "community.kafka-policy.retry.base-backoff=1s"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UploadPolicyProperties.class);
                    assertThat(context).hasSingleBean(KafkaPolicyProperties.class);
                    assertThat(context).hasSingleBean(UploadPolicyDecisions.class);
                    assertThat(context.getBean(UploadPolicyProperties.class).getAllowedMimeTypes()).containsExactly("image/png");
                    assertThat(context.getBean(KafkaPolicyProperties.class).getRetry().getMaxAttempts()).isEqualTo(3);
                    assertThat(context.getBean(UploadPolicyDecisions.class).allowsMimeType("image/png")).isTrue();
                    assertThat(context.getBean(UploadPolicyDecisions.class).maxFileSizeBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
                });
    }
}
