package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({FeatureFlagProperties.class, DegradationProperties.class})
public class RuntimePolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagDecisions featureFlagDecisions(FeatureFlagProperties properties) {
        return new FeatureFlagDecisions(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DegradationDecisions degradationDecisions(DegradationProperties properties) {
        return new DegradationDecisions(properties);
    }
}
