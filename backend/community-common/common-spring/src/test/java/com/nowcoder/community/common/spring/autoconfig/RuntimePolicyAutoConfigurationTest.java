package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
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
                        "community.degradation.search=best-effort"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureFlagDecisions.class);
                    assertThat(context).hasSingleBean(DegradationDecisions.class);
                    assertThat(context.getBean(FeatureFlagDecisions.class).enabled("post-publishing")).isTrue();
                    assertThat(context.getBean(DegradationDecisions.class).mode("search")).isEqualTo("best-effort");
                });
    }

    @Test
    void backsOffWhenDecisionBeansAlreadyExist() {
        contextRunner
                .withUserConfiguration(CustomDecisionConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FeatureFlagDecisions.class);
                    assertThat(context).hasSingleBean(DegradationDecisions.class);
                    assertThat(context.getBean(FeatureFlagDecisions.class).enabled("custom")).isTrue();
                    assertThat(context.getBean(DegradationDecisions.class).mode("custom")).isEqualTo("off");
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

        @Bean
        DegradationDecisions degradationDecisions() {
            DegradationProperties properties = new DegradationProperties();
            properties.getModes().put("custom", "off");
            return new DegradationDecisions(properties);
        }
    }
}
