package com.nowcoder.community.infra.startup.autoconfig;

import com.nowcoder.community.infra.startup.StartupValidation;
import com.nowcoder.community.infra.startup.StartupValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Objects;

/**
 * prod profile 下启用启动期校验（fail-closed）。
 *
 * <p>说明：该 auto-config 不依赖 servlet/reactive 细节，可在所有服务与单体中生效。</p>
 */
@AutoConfiguration
@Profile("prod")
public class StartupValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StartupValidation startupValidation(ObjectProvider<StartupValidator> validatorsProvider) {
        List<StartupValidator> validators = validatorsProvider.orderedStream()
                .filter(Objects::nonNull)
                .toList();
        return new StartupValidation(validators);
    }

    @Bean
    @ConditionalOnMissingBean(name = "startupValidationRunner")
    public ApplicationRunner startupValidationRunner(StartupValidation startupValidation, Environment environment) {
        return args -> startupValidation.validateOrThrow(environment);
    }
}
