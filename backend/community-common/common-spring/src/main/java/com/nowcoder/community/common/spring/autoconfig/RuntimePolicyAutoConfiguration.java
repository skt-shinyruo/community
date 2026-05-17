package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.degradation.DegradationDecisions;
import com.nowcoder.community.common.spring.degradation.DegradationProperties;
import com.nowcoder.community.common.spring.feature.FeatureFlagDecisions;
import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
import com.nowcoder.community.common.spring.policy.CachePolicyDecisions;
import com.nowcoder.community.common.spring.policy.CachePolicyProperties;
import com.nowcoder.community.common.spring.policy.KafkaPolicyDecisions;
import com.nowcoder.community.common.spring.policy.KafkaPolicyProperties;
import com.nowcoder.community.common.spring.policy.UploadPolicyDecisions;
import com.nowcoder.community.common.spring.policy.UploadPolicyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({
        FeatureFlagProperties.class,
        DegradationProperties.class,
        CachePolicyProperties.class,
        UploadPolicyProperties.class,
        KafkaPolicyProperties.class
})
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

    @Bean
    @ConditionalOnMissingBean
    public CachePolicyDecisions cachePolicyDecisions(CachePolicyProperties properties) {
        return new CachePolicyDecisions(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public UploadPolicyDecisions uploadPolicyDecisions(UploadPolicyProperties properties) {
        return new UploadPolicyDecisions(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaPolicyDecisions kafkaPolicyDecisions(KafkaPolicyProperties properties) {
        return new KafkaPolicyDecisions(properties);
    }
}
