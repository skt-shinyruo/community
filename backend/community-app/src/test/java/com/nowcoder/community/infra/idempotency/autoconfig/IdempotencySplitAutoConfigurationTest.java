package com.nowcoder.community.infra.idempotency.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.idempotency.IdempotencyGuard;
import com.nowcoder.community.infra.idempotency.IdempotencyStore;
import com.nowcoder.community.infra.idempotency.JdbcIdempotencyStore;
import com.nowcoder.community.infra.idempotency.RedisIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencySplitAutoConfigurationTest {

    @Test
    void disabledShouldNotCreateBeansEvenIfDependenciesPresent() {
        runner()
                .withPropertyValues(
                        "http.idempotency.enabled=false",
                        "http.idempotency.store=DB"
                )
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBeanProvider(IdempotencyStore.class).getIfAvailable()).isNull();
                    assertThat(context.getBeanProvider(IdempotencyGuard.class).getIfAvailable()).isNull();
                });
    }

    @Test
    void enabledRedisShouldCreateRedisStoreAndGuard() {
        runner()
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=REDIS"
                )
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBeanProvider(IdempotencyStore.class).getIfAvailable())
                            .isInstanceOf(RedisIdempotencyStore.class);
                    assertThat(context.getBeanProvider(IdempotencyGuard.class).getIfAvailable()).isNotNull();
                });
    }

    @Test
    void enabledDbShouldCreateJdbcStoreAndGuard() {
        runner()
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=DB"
                )
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBeanProvider(IdempotencyStore.class).getIfAvailable())
                            .isInstanceOf(JdbcIdempotencyStore.class);
                    assertThat(context.getBeanProvider(IdempotencyGuard.class).getIfAvailable()).isNotNull();
                });
    }

    @Test
    void enabledDbWithoutJdbcTemplateShouldFailFast() {
        runner()
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=DB"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("http.idempotency.store=DB 需要 JdbcTemplate"));
    }

    @Test
    void enabledRedisWithoutRedisTemplateShouldFailFast() {
        runner()
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=REDIS"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("http.idempotency.store=REDIS 需要 StringRedisTemplate"));
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdempotencyAutoConfiguration.class,
                        loadAutoConfiguration("com.nowcoder.community.infra.idempotency.autoconfig.JdbcIdempotencyAutoConfiguration"),
                        loadAutoConfiguration("com.nowcoder.community.infra.idempotency.autoconfig.RedisIdempotencyAutoConfiguration"),
                        loadAutoConfiguration("com.nowcoder.community.infra.idempotency.autoconfig.IdempotencyGuardAutoConfiguration")
                ));
    }

    private static Class<?> loadAutoConfiguration(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing auto-configuration class: " + className, e);
        }
    }
}

