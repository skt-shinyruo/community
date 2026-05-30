package com.nowcoder.community.common.idempotency.autoconfig;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IdempotencyGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyGuardAutoConfiguration.class))
            .withPropertyValues("http.idempotency.enabled=true");

    @Test
    void backsOffWhenJsonCodecBeanMissing() {
        contextRunner
                .withUserConfiguration(StoreOnlyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdempotencyGuard.class);
                });
    }

    @Test
    void createsGuardWhenStoreAndJsonCodecBeansExist() {
        contextRunner
                .withUserConfiguration(StoreAndJsonCodecConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreOnlyConfiguration {

        @Bean
        IdempotencyStore idempotencyStore() {
            return mock(IdempotencyStore.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreAndJsonCodecConfiguration {

        @Bean
        IdempotencyStore idempotencyStore() {
            return mock(IdempotencyStore.class);
        }

        @Bean
        JsonCodec jsonCodec() {
            return new JacksonJsonCodec(JsonMappers.standard());
        }
    }
}
