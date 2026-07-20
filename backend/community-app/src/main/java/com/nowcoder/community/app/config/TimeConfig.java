package com.nowcoder.community.app.config;

import com.nowcoder.community.common.id.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    UuidV7Generator uuidV7Generator(Clock systemClock) {
        return new UuidV7Generator(systemClock);
    }
}
