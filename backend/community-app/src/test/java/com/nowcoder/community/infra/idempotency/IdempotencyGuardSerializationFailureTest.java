package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardSerializationFailureTest {

    @Test
    void executeRequired_shouldNotThrow_whenResponseSerializationFails_afterSupplierSucceeded() throws Exception {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.toJson(any())).thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000001");

        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(UUID.class), anyString(), eq("hash-1"), any(Duration.class))).thenReturn(true);

        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec, store, null, new IdempotencyProperties());

        Object result = new Object();
        Object returned = guard.executeRequired("op", userId, "k1", "hash-1", null, Object.class, () -> result);

        assertThat(returned).isSameAs(result);
        verify(store).saveSuccess(eq("op"), eq(userId), eq("k1"), eq("hash-1"), eq("null"), any(Duration.class));
        verify(store, never()).delete(anyString(), any(UUID.class), anyString());
    }
}
