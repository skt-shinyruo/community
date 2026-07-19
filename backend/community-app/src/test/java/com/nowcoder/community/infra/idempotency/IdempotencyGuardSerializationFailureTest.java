package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardSerializationFailureTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Test
    void executeRequiredShouldPropagateResponseSerializationFailureWithoutPersistingSuccess() {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        JsonCodecException failure = new JsonCodecException("boom", new RuntimeException("boom"));
        when(jsonCodec.toJson(any())).thenThrow(failure);
        TransactionalIdempotencyStore store = enlistedAcquiredStore();
        IdempotencyGuard guard = guard(jsonCodec, store);

        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, Object.class, Object::new))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(failure);

        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
        verify(store, never()).extendProcessing(anyString(), any(), anyString(), any(Duration.class));
        verify(store, never()).delete(anyString(), any(), anyString());
    }

    @Test
    void executeRequiredShouldRejectNullBusinessResult() {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        TransactionalIdempotencyStore store = enlistedAcquiredStore();
        IdempotencyGuard guard = guard(jsonCodec, store);

        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, Object.class, () -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency response");

        verify(jsonCodec, never()).toJson(any());
        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null"})
    void executeRequiredShouldRejectEmptyOrNullJson(String serialized) {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.toJson(any())).thenReturn(serialized);
        TransactionalIdempotencyStore store = enlistedAcquiredStore();
        IdempotencyGuard guard = guard(jsonCodec, store);

        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, Object.class, Object::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency response");

        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }

    private static TransactionalIdempotencyStore enlistedAcquiredStore() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        when(store.tryAcquireProcessing(anyString(), any(UUID.class), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        return store;
    }

    private static IdempotencyGuard guard(JsonCodec jsonCodec, IdempotencyStore store) {
        return new IdempotencyGuard(jsonCodec, store, null, new IdempotencyProperties());
    }
}
