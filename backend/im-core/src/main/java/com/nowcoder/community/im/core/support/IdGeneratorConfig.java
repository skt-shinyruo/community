package com.nowcoder.community.im.core.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public IdGenerator idGenerator(@Value("${im.id.node-id:0}") long nodeId) {
        return new SnowflakeIdGenerator(Clock.systemUTC(), nodeId);
    }
}

