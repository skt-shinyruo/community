package com.nowcoder.community.platform.startup;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * prod profile 下启用启动期校验。
 *
 * <p>说明：该配置类本身不依赖 servlet/reactive 细节，后续可被 common auto-configuration 统一引入。</p>
 */
@Configuration
@Profile("prod")
public class StartupValidationAutoConfig {

    @Bean
    public StartupValidation startupValidation() {
        return new StartupValidation();
    }

    @Bean
    public ApplicationRunner startupValidationRunner(StartupValidation startupValidation, Environment environment) {
        return args -> startupValidation.validateOrThrow(environment);
    }
}

