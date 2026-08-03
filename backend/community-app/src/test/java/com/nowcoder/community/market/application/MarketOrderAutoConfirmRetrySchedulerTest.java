package com.nowcoder.community.market.application;

import com.nowcoder.community.market.domain.repository.MarketOrderRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketOrderAutoConfirmRetrySchedulerTest {

    @Test
    void deferShouldOnlyReportAStillDueOrderAsRescheduled() {
        MarketOrderRepository repository = mock(MarketOrderRepository.class);
        UUID orderId = uuid(1);
        Date asOf = new Date(1_000L);
        Date nextAttemptAt = new Date(61_000L);
        when(repository.deferAutoConfirm(orderId, asOf, nextAttemptAt)).thenReturn(1);

        boolean deferred = new MarketOrderAutoConfirmRetryScheduler(repository)
                .defer(orderId, asOf, nextAttemptAt);

        assertThat(deferred).isTrue();
        verify(repository).deferAutoConfirm(orderId, asOf, nextAttemptAt);
    }
}
