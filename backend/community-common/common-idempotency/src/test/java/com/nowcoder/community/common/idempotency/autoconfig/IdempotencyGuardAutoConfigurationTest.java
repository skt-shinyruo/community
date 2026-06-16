package com.nowcoder.community.common.idempotency.autoconfig;

import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.web.autoconfig.ServletWebInfraAutoConfiguration;
import com.nowcoder.community.common.web.autoconfig.WebInfraAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void createsDbBackedGuardAfterJdbcTemplateAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdempotencyAutoConfiguration.class,
                        JdbcIdempotencyAutoConfiguration.class,
                        RedisIdempotencyAutoConfiguration.class,
                        IdempotencyGuardAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class
                ))
                .withUserConfiguration(DataSourceAndJsonCodecConfiguration.class)
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=DB"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                });
    }

    @Test
    void createsDbBackedGuardWhenJsonCodecComesFromServletWebAutoConfiguration() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdempotencyAutoConfiguration.class,
                        JdbcIdempotencyAutoConfiguration.class,
                        RedisIdempotencyAutoConfiguration.class,
                        IdempotencyGuardAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        JacksonAutoConfiguration.class,
                        WebInfraAutoConfiguration.class,
                        ServletWebInfraAutoConfiguration.class
                ))
                .withUserConfiguration(DataSourceConfiguration.class)
                .withPropertyValues(
                        "http.idempotency.enabled=true",
                        "http.idempotency.store=DB"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JsonCodec.class);
                    assertThat(context).hasSingleBean(IdempotencyStore.class);
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreOnlyConfiguration {

        @Bean
        IdempotencyStore idempotencyStore() {
            return new NoopIdempotencyStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreAndJsonCodecConfiguration {

        @Bean
        IdempotencyStore idempotencyStore() {
            return new NoopIdempotencyStore();
        }

        @Bean
        JsonCodec jsonCodec() {
            return new JacksonJsonCodec(JsonMappers.standard());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JsonCodecConfiguration {

        @Bean
        JsonCodec jsonCodec() {
            return new JacksonJsonCodec(JsonMappers.standard());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:idempotency-autoconfig;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceAndJsonCodecConfiguration extends DataSourceConfiguration {

        @Bean
        JsonCodec jsonCodec() {
            return new JacksonJsonCodec(JsonMappers.standard());
        }
    }

    private static class NoopIdempotencyStore implements IdempotencyStore {

        @Override
        public boolean tryAcquireProcessing(String operation, UUID userId, String key, Duration ttl) {
            return true;
        }

        @Override
        public Entry get(String operation, UUID userId, String key) {
            return null;
        }

        @Override
        public void saveSuccess(String operation, UUID userId, String key, String successJson, Duration ttl) {
        }

        @Override
        public void extendProcessing(String operation, UUID userId, String key, Duration ttl) {
        }

        @Override
        public void delete(String operation, UUID userId, String key) {
        }
    }
}
