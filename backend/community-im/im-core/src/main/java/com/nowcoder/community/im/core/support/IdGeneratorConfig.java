package com.nowcoder.community.im.core.support;

import com.nowcoder.community.common.id.UuidV7Generator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    @Bean
    public IdGenerator idGenerator() {
        UuidV7Generator generator = new UuidV7Generator();
        return generator::next;
    }
}
