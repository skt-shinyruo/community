package com.nowcoder.community.infra.idempotency.autoconfig;

import com.nowcoder.community.infra.idempotency.IdempotencyProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyAutoConfiguration {
}
