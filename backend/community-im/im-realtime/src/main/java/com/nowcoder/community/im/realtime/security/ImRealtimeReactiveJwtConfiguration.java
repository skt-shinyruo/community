package com.nowcoder.community.im.realtime.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class ImRealtimeReactiveJwtConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder reactiveJwtDecoder(JwtDecoder jwtDecoder) {
        return token -> Mono.fromCallable(() -> jwtDecoder.decode(token));
    }
}
