package com.nowcoder.community.common.spring.autoconfig;

import com.nowcoder.community.common.spring.feature.FeatureFlagProperties;
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
        UploadPolicyProperties.class,
        KafkaPolicyProperties.class
})
public class RuntimePolicyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UploadPolicyDecisions uploadPolicyDecisions(UploadPolicyProperties properties) {
        return new UploadPolicyDecisions(properties);
    }
}
