package com.nowcoder.community.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardStoreFailureTest {

    @Test
    void executeRequiredShouldExtendProcessingAndReturnConflictWhenSaveSuccessFails() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), anyInt(), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new RuntimeException("redis down"))
                .when(store).saveSuccess(anyString(), anyInt(), anyString(), anyString(), any(Duration.class));

        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setSuccessTtl(Duration.ofMinutes(10));

        IdempotencyGuard guard = new IdempotencyGuard(new ObjectMapper(), store, null, properties);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired("op", 1, "k1", String.class, () -> {
            supplierCalls.incrementAndGet();
            return "OK";
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getErrorCode().getCode()).isEqualTo(409);
                    assertThat(error.getMessage()).isEqualTo("请求结果确认中，请稍后重试");
                });

        assertThat(supplierCalls).hasValue(1);
        verify(store).extendProcessing(eq("op"), eq(1), eq("k1"), eq(Duration.ofMinutes(10)));
        verify(store, never()).delete(anyString(), anyInt(), anyString());
    }
}
