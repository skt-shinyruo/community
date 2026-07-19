package com.nowcoder.community.infra.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.idempotency.IdempotencyGuard;
import com.nowcoder.community.common.idempotency.IdempotencyProperties;
import com.nowcoder.community.common.idempotency.IdempotencyStore;
import com.nowcoder.community.common.idempotency.JdbcIdempotencyStore;
import com.nowcoder.community.common.idempotency.TransactionalIdempotencyStore;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(IdempotencyBusinessAtomicitySpringTest.AtomicityTestConfiguration.class)
@ActiveProfiles("idempotency-business-atomicity")
class IdempotencyBusinessAtomicitySpringTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionalIdempotencyStore store;

    @Autowired
    private IdempotencyGuard guard;

    @Autowired
    private AtomicBusinessService businessService;

    @BeforeEach
    void prepareSchema() {
        jdbcTemplate.execute("""
                create table if not exists http_idempotency (
                  id binary(16) primary key,
                  operation varchar(64) not null,
                  user_id binary(16) not null,
                  idem_key varchar(128) not null,
                  request_hash varchar(64) not null,
                  status varchar(16) not null,
                  response_json clob,
                  processing_expires_at timestamp,
                  success_expires_at timestamp,
                  created_at timestamp default current_timestamp,
                  updated_at timestamp default current_timestamp,
                  unique (operation, user_id, idem_key)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists idempotency_business_probe (
                  id integer primary key,
                  payload varchar(128) not null
                )
                """);
        jdbcTemplate.update("delete from http_idempotency");
        jdbcTemplate.update("delete from idempotency_business_probe");
    }

    @Test
    void serializationFailureShouldRollBackBusinessAndIdempotencyRows() {
        JsonCodec failingCodec = mock(JsonCodec.class);
        JsonCodecException failure = new JsonCodecException("serialization failed", new RuntimeException("boom"));
        when(failingCodec.toJson(any())).thenThrow(failure);
        IdempotencyGuard failingGuard = new IdempotencyGuard(
                failingCodec, store, null, new IdempotencyProperties());
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> businessService.execute(
                failingGuard, "serialize-failure", "hash-serialize", 1, supplierCalls))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(failure);

        assertAllRowsRolledBack();
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void rejectedSuccessTransitionShouldRollBackBusinessAndIdempotencyRows() {
        IdempotencyGuard rejectingGuard = new IdempotencyGuard(
                jsonCodec(), new SaveRejectingStore(store), null, new IdempotencyProperties());
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> businessService.execute(
                rejectingGuard, "save-rejected", "hash-save-rejected", 2, supplierCalls))
                .isInstanceOfAny(IllegalStateException.class, BusinessException.class);

        assertAllRowsRolledBack();
        assertThat(supplierCalls).hasValue(1);
    }

    @Test
    void acquiredResultAndBusinessWriteShouldCommitTogetherAndReplayWithoutSupplier() {
        AtomicInteger supplierCalls = new AtomicInteger();

        String first = businessService.execute(guard, "success", "hash-success", 3, supplierCalls);
        String replay = businessService.execute(guard, "success", "hash-success", 3, supplierCalls);

        String status = jdbcTemplate.queryForObject(
                "select status from http_idempotency where idem_key = ?", String.class, "success");
        String responseJson = jdbcTemplate.queryForObject(
                "select response_json from http_idempotency where idem_key = ?", String.class, "success");
        assertAll(
                () -> assertThat(first).isEqualTo("OK-3"),
                () -> assertThat(replay).isEqualTo("OK-3"),
                () -> assertThat(supplierCalls).hasValue(1),
                () -> assertThat(rowCount("idempotency_business_probe")).isOne(),
                () -> assertThat(rowCount("http_idempotency")).isOne(),
                () -> assertThat(status).isEqualTo("S"),
                () -> assertThat(responseJson).isNotBlank().isNotEqualTo("null")
        );
    }

    private void assertAllRowsRolledBack() {
        assertAll(
                () -> assertThat(rowCount("idempotency_business_probe")).isZero(),
                () -> assertThat(rowCount("http_idempotency")).isZero()
        );
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("idempotency-business-atomicity")
    @EnableTransactionManagement
    static class AtomicityTestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:idempotency-business-atomicity;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionalIdempotencyStore idempotencyStore(JdbcTemplate jdbcTemplate) {
            return new JdbcIdempotencyStore(jdbcTemplate);
        }

        @Bean
        JsonCodec jsonCodec() {
            return IdempotencyBusinessAtomicitySpringTest.jsonCodec();
        }

        @Bean
        IdempotencyGuard idempotencyGuard(JsonCodec jsonCodec, TransactionalIdempotencyStore store) {
            return new IdempotencyGuard(jsonCodec, store, null, new IdempotencyProperties());
        }

        @Bean
        AtomicBusinessService atomicBusinessService(JdbcTemplate jdbcTemplate) {
            return new AtomicBusinessService(jdbcTemplate);
        }
    }

    static class AtomicBusinessService {

        private final JdbcTemplate jdbcTemplate;

        AtomicBusinessService(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public String execute(IdempotencyGuard guard,
                              String key,
                              String requestHash,
                              int probeId,
                              AtomicInteger supplierCalls) {
            return guard.executeRequired(
                    "atomic:test",
                    USER_ID,
                    key,
                    requestHash,
                    null,
                    String.class,
                    () -> {
                        supplierCalls.incrementAndGet();
                        jdbcTemplate.update(
                                "insert into idempotency_business_probe(id, payload) values (?, ?)",
                                probeId,
                                "probe-" + probeId
                        );
                        return "OK-" + probeId;
                    }
            );
        }
    }

    private static final class SaveRejectingStore implements TransactionalIdempotencyStore {

        private final TransactionalIdempotencyStore delegate;

        private SaveRejectingStore(TransactionalIdempotencyStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isEnlistedInCurrentTransaction() {
            return delegate.isEnlistedInCurrentTransaction();
        }

        @Override
        public boolean tryAcquireProcessing(
                String operation, UUID userId, String key, String requestHash, Duration ttl) {
            return delegate.tryAcquireProcessing(operation, userId, key, requestHash, ttl);
        }

        @Override
        public Entry get(String operation, UUID userId, String key) {
            return delegate.get(operation, userId, key);
        }

        @Override
        public boolean saveSuccess(
                String operation,
                UUID userId,
                String key,
                String requestHash,
                String successJson,
                Duration ttl) {
            return false;
        }

        @Override
        public void extendProcessing(String operation, UUID userId, String key, Duration ttl) {
            delegate.extendProcessing(operation, userId, key, ttl);
        }

        @Override
        public void delete(String operation, UUID userId, String key) {
            delegate.delete(operation, userId, key);
        }
    }
}
