package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcIdempotencyStoreTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void tryAcquireProcessingShouldInsertUuidPrimaryKey() {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbcTemplate);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        boolean acquired = store.tryAcquireProcessing("post:create", USER_ID, "idem-1", "hash-a", Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("insert into http_idempotency");
        assertThat(sqlCaptor.getValue()).contains("id");
        Object[] args = argsCaptor.getValue();
        assertThat(args).isNotEmpty();
        assertThat(args[0]).isInstanceOf(byte[].class);
        UUID id = BinaryUuidCodec.fromBytes((byte[]) args[0]);
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void tryAcquireProcessingShouldPersistRequestHashWhenProvided() {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbcTemplate);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

        boolean acquired = store.tryAcquireProcessing("wallet:recharge", USER_ID, "idem-1", "hash-a", Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("request_hash");
        assertThat(argsCaptor.getValue()).contains("hash-a");
    }

    @Test
    void writesShouldRejectMissingRequestHash() {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbcTemplate);

        assertThatThrownBy(() -> store.tryAcquireProcessing("post:create", USER_ID, "idem-1", " ", Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestHash");
        assertThatThrownBy(() -> store.saveSuccess("post:create", USER_ID, "idem-1", null, "{}", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestHash");
        assertThatThrownBy(() -> store.tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-1",
                "hash-a\rhash-b",
                Duration.ofSeconds(30)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line break");
        assertThatThrownBy(() -> store.saveSuccess(
                "post:create",
                USER_ID,
                "idem-1",
                "h".repeat(65),
                "{}",
                Duration.ofHours(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void expiredProcessingShouldNotBeReacquiredOrOverwritten() {
        StoreFixture fixture = storeFixture();
        assertThat(fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-expired",
                "hash-a",
                Duration.ofSeconds(-5)
        )).isTrue();

        boolean reacquired = fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-expired",
                "hash-b",
                Duration.ofSeconds(30)
        );
        String persistedHash = fixture.jdbcTemplate().queryForObject(
                "select request_hash from http_idempotency where idem_key = ?",
                String.class,
                "idem-expired"
        );

        assertAll(
                () -> assertThat(reacquired).isFalse(),
                () -> assertThat(persistedHash).isEqualTo("hash-a")
        );
    }

    @Test
    void getShouldKeepExpiredProcessingAsProcessing() {
        StoreFixture fixture = storeFixture();
        assertThat(fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-expired",
                "hash-a",
                Duration.ofSeconds(-5)
        )).isTrue();

        IdempotencyStore.Entry entry = fixture.store().get("post:create", USER_ID, "idem-expired");
        Integer rowCount = fixture.jdbcTemplate().queryForObject(
                "select count(*) from http_idempotency where idem_key = ?",
                Integer.class,
                "idem-expired"
        );

        assertAll(
                () -> assertThat(entry).isEqualTo(new IdempotencyStore.Entry(
                        IdempotencyStore.Status.PROCESSING,
                        null,
                        "hash-a"
                )),
                () -> assertThat(rowCount).isOne()
        );
    }

    @Test
    void saveSuccessShouldUpdateMatchingProcessingRow() {
        StoreFixture fixture = storeFixture();
        assertThat(fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-success",
                "hash-a",
                Duration.ofSeconds(30)
        )).isTrue();

        boolean saved = fixture.store().saveSuccess(
                "post:create",
                USER_ID,
                "idem-success",
                "hash-a",
                "{\"id\":1}",
                Duration.ofHours(1)
        );
        IdempotencyStore.Entry entry = fixture.store().get("post:create", USER_ID, "idem-success");

        assertAll(
                () -> assertThat(saved).isTrue(),
                () -> assertThat(entry).isEqualTo(new IdempotencyStore.Entry(
                        IdempotencyStore.Status.SUCCESS,
                        "{\"id\":1}",
                        "hash-a"
                ))
        );
    }

    @Test
    void saveSuccessShouldRejectWrongRequestHashWithoutOverwriting() {
        StoreFixture fixture = processingFixture("idem-wrong-hash", "hash-a");
        PersistedEntry before = persistedEntry(fixture, "idem-wrong-hash");

        boolean saved = fixture.store().saveSuccess(
                "post:create",
                USER_ID,
                "idem-wrong-hash",
                "hash-b",
                "{\"id\":2}",
                Duration.ofHours(1)
        );

        assertAll(
                () -> assertThat(saved).isFalse(),
                () -> assertThat(persistedEntry(fixture, "idem-wrong-hash")).isEqualTo(before)
        );
    }

    @Test
    void saveSuccessShouldRejectExistingSuccessWithoutOverwriting() {
        StoreFixture fixture = processingFixture("idem-existing-success", "hash-a");
        forceState(fixture, "idem-existing-success", "S", "{\"id\":1}", Instant.now().plusSeconds(300));
        PersistedEntry before = persistedEntry(fixture, "idem-existing-success");

        boolean saved = fixture.store().saveSuccess(
                "post:create",
                USER_ID,
                "idem-existing-success",
                "hash-a",
                "{\"id\":2}",
                Duration.ofHours(1)
        );

        assertAll(
                () -> assertThat(saved).isFalse(),
                () -> assertThat(persistedEntry(fixture, "idem-existing-success")).isEqualTo(before)
        );
    }

    @Test
    void saveSuccessShouldRejectIndeterminateWithoutOverwriting() {
        StoreFixture fixture = processingFixture("idem-indeterminate", "hash-a");
        forceState(fixture, "idem-indeterminate", "I", null, null);
        PersistedEntry before = persistedEntry(fixture, "idem-indeterminate");

        boolean saved = fixture.store().saveSuccess(
                "post:create",
                USER_ID,
                "idem-indeterminate",
                "hash-a",
                "{\"id\":2}",
                Duration.ofHours(1)
        );

        assertAll(
                () -> assertThat(saved).isFalse(),
                () -> assertThat(persistedEntry(fixture, "idem-indeterminate")).isEqualTo(before)
        );
    }

    @Test
    void saveSuccessShouldRejectMissingRowWithoutInserting() {
        StoreFixture fixture = storeFixture();

        boolean saved = fixture.store().saveSuccess(
                "post:create",
                USER_ID,
                "idem-missing",
                "hash-a",
                "{\"id\":1}",
                Duration.ofHours(1)
        );
        Integer rowCount = fixture.jdbcTemplate().queryForObject(
                "select count(*) from http_idempotency where idem_key = ?",
                Integer.class,
                "idem-missing"
        );

        assertAll(
                () -> assertThat(saved).isFalse(),
                () -> assertThat(rowCount).isZero()
        );
    }

    @Test
    void saveSuccessShouldReturnTrueOnlyWhenExactlyOneRowWasUpdated() {
        JdbcIdempotencyStore store = new JdbcIdempotencyStore(jdbcTemplate);
        when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1, 0, 2);

        boolean oneRow = store.saveSuccess("post:create", USER_ID, "idem-1", "hash-a", "{}", Duration.ofHours(1));
        boolean noRows = store.saveSuccess("post:create", USER_ID, "idem-2", "hash-a", "{}", Duration.ofHours(1));
        boolean twoRows = store.saveSuccess("post:create", USER_ID, "idem-3", "hash-a", "{}", Duration.ofHours(1));

        assertAll(
                () -> assertThat(oneRow).isTrue(),
                () -> assertThat(noRows).isFalse(),
                () -> assertThat(twoRows).isFalse()
        );
    }

    @Test
    void getShouldMapIndeterminateStateWithoutDeletingIt() {
        StoreFixture fixture = processingFixture("idem-indeterminate", "hash-a");
        forceState(fixture, "idem-indeterminate", "I", null, null);

        IdempotencyStore.Entry entry = fixture.store().get("post:create", USER_ID, "idem-indeterminate");
        Integer rowCount = fixture.jdbcTemplate().queryForObject(
                "select count(*) from http_idempotency where idem_key = ?",
                Integer.class,
                "idem-indeterminate"
        );

        assertAll(
                () -> assertThat(entry).isEqualTo(new IdempotencyStore.Entry(
                        IdempotencyStore.Status.INDETERMINATE,
                        null,
                        "hash-a"
                )),
                () -> assertThat(rowCount).isOne()
        );
    }

    @Test
    void expiredSuccessShouldBeReacquiredAsProcessing() {
        StoreFixture fixture = processingFixture("idem-expired-success", "hash-a");
        forceState(fixture, "idem-expired-success", "S", "{\"id\":1}", Instant.now().minusSeconds(5));

        boolean reacquired = fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-expired-success",
                "hash-b",
                Duration.ofSeconds(30)
        );
        IdempotencyStore.Entry entry = fixture.store().get("post:create", USER_ID, "idem-expired-success");

        assertAll(
                () -> assertThat(reacquired).isTrue(),
                () -> assertThat(entry).isEqualTo(new IdempotencyStore.Entry(
                        IdempotencyStore.Status.PROCESSING,
                        null,
                        "hash-b"
                ))
        );
    }

    @Test
    void indeterminateShouldNotBeReacquired() {
        StoreFixture fixture = processingFixture("idem-indeterminate", "hash-a");
        forceState(fixture, "idem-indeterminate", "I", null, null);

        boolean reacquired = fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                "idem-indeterminate",
                "hash-b",
                Duration.ofSeconds(30)
        );

        assertAll(
                () -> assertThat(reacquired).isFalse(),
                () -> assertThat(persistedEntry(fixture, "idem-indeterminate").requestHash()).isEqualTo("hash-a")
        );
    }

    @Test
    void getShouldDeleteExpiredSuccess() {
        StoreFixture fixture = processingFixture("idem-expired-success", "hash-a");
        forceState(fixture, "idem-expired-success", "S", "{\"id\":1}", Instant.now().minusSeconds(5));

        IdempotencyStore.Entry entry = fixture.store().get("post:create", USER_ID, "idem-expired-success");
        Integer rowCount = fixture.jdbcTemplate().queryForObject(
                "select count(*) from http_idempotency where idem_key = ?",
                Integer.class,
                "idem-expired-success"
        );

        assertAll(
                () -> assertThat(entry).isNull(),
                () -> assertThat(rowCount).isZero()
        );
    }

    private StoreFixture processingFixture(String key, String requestHash) {
        StoreFixture fixture = storeFixture();
        assertThat(fixture.store().tryAcquireProcessing(
                "post:create",
                USER_ID,
                key,
                requestHash,
                Duration.ofSeconds(30)
        )).isTrue();
        return fixture;
    }

    private void forceState(StoreFixture fixture,
                            String key,
                            String status,
                            String responseJson,
                            Instant successExpiresAt) {
        fixture.jdbcTemplate().update(
                """
                        update http_idempotency
                        set status = ?, response_json = ?, processing_expires_at = null, success_expires_at = ?
                        where idem_key = ?
                        """,
                status,
                responseJson,
                successExpiresAt == null ? null : Timestamp.from(successExpiresAt),
                key
        );
    }

    private PersistedEntry persistedEntry(StoreFixture fixture, String key) {
        return fixture.jdbcTemplate().queryForObject(
                """
                        select status, request_hash, response_json, processing_expires_at, success_expires_at
                        from http_idempotency
                        where idem_key = ?
                        """,
                (rs, rowNum) -> new PersistedEntry(
                        rs.getString("status"),
                        rs.getString("request_hash"),
                        rs.getString("response_json"),
                        rs.getTimestamp("processing_expires_at"),
                        rs.getTimestamp("success_expires_at")
                ),
                key
        );
    }

    private StoreFixture storeFixture() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:jdbc-idempotency-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.execute(
                """
                        create table http_idempotency (
                          id binary(16) primary key,
                          operation varchar(128) not null,
                          user_id binary(16) not null,
                          idem_key varchar(255) not null,
                          request_hash varchar(64) not null,
                          status char(1) not null,
                          response_json clob,
                          processing_expires_at timestamp,
                          success_expires_at timestamp,
                          updated_at timestamp default current_timestamp,
                          unique (operation, user_id, idem_key)
                        )
                        """
        );
        return new StoreFixture(new JdbcIdempotencyStore(template), template);
    }

    private record StoreFixture(JdbcIdempotencyStore store, JdbcTemplate jdbcTemplate) {
    }

    private record PersistedEntry(String status,
                                  String requestHash,
                                  String responseJson,
                                  Timestamp processingExpiresAt,
                                  Timestamp successExpiresAt) {
    }
}
