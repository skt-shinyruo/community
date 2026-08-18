package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardTtlTest {

    @Test
    void executeRequiredShouldUseConfiguredTtlWithTransactionalStore() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        when(store.tryAcquireProcessing(anyString(), any(UUID.class), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(UUID.class), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setProcessingTtl(Duration.ofSeconds(45));
        properties.setSuccessTtl(Duration.ofMinutes(10));
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, properties);
        UUID userId = uuid(1);

        guard.executeRequired("op", userId, "k1", "hash-1", null, String.class, () -> "OK");

        verify(store).tryAcquireProcessing("op", userId, "k1", "hash-1", Duration.ofSeconds(45));
        verify(store).saveSuccess("op", userId, "k1", "hash-1", "\"OK\"", Duration.ofMinutes(10));
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
