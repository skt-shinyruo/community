package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        boolean acquired = store.tryAcquireProcessing("post:create", USER_ID, "idem-1", Duration.ofSeconds(30));

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
}
