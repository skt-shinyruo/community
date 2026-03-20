package com.nowcoder.community.infra.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardSerializationFailureTest {

    @Test
    void executeRequired_shouldNotThrow_whenResponseSerializationFails_afterSupplierSucceeded() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), anyInt(), anyString(), any(Duration.class))).thenReturn(true);

        IdempotencyGuard guard = new IdempotencyGuard(objectMapper, store, null, new IdempotencyProperties());

        Object result = new Object();
        Object returned = guard.executeRequired("op", 1, "k1", Object.class, () -> result);

        assertThat(returned).isSameAs(result);
        verify(store).saveSuccess(eq("op"), eq(1), eq("k1"), eq("null"), any(Duration.class));
        verify(store, never()).delete(anyString(), anyInt(), anyString());
    }
}

