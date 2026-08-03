package com.nowcoder.community.search.infrastructure.reindex;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import com.nowcoder.community.search.application.SearchReindexLeasePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSearchReindexLeaseAdapterTest {

    @Test
    void leaseShouldRenewBeforeASubSecondTtlExpiresAndReleaseOnlyItsLock() {
        SingleFlightTaskGuard taskGuard = mock(SingleFlightTaskGuard.class);
        ScheduledExecutorService renewer = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> renewal = mock(ScheduledFuture.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "owner-token");
        Duration ttl = Duration.ofMillis(900);
        ArgumentCaptor<Runnable> heartbeatCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskGuard.tryAcquire("search:reindex", ttl)).thenReturn(lock);
        doReturn(renewal).when(renewer).scheduleAtFixedRate(
                heartbeatCaptor.capture(), eq(300L), eq(300L), eq(TimeUnit.MILLISECONDS)
        );
        when(taskGuard.refresh(lock, ttl)).thenReturn(true);
        RedisSearchReindexLeaseAdapter adapter = new RedisSearchReindexLeaseAdapter(taskGuard, renewer, false);

        Optional<SearchReindexLeasePort.Lease> acquired = adapter.tryAcquire(ttl);
        assertThat(acquired).isPresent();
        SearchReindexLeasePort.Lease lease = acquired.orElseThrow();

        heartbeatCaptor.getValue().run();
        assertThat(lease.isValid()).isTrue();
        verify(taskGuard).refresh(lock, ttl);

        lease.close();
        assertThat(lease.isValid()).isFalse();
        verify(renewal).cancel(false);
        verify(taskGuard).release(lock);
    }

    @Test
    void leaseShouldStayInvalidAfterAnyRenewalFailure() {
        SingleFlightTaskGuard taskGuard = mock(SingleFlightTaskGuard.class);
        ScheduledExecutorService renewer = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> renewal = mock(ScheduledFuture.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "owner-token");
        Duration ttl = Duration.ofSeconds(30);
        ArgumentCaptor<Runnable> heartbeatCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskGuard.tryAcquire("search:reindex", ttl)).thenReturn(lock);
        doReturn(renewal).when(renewer).scheduleAtFixedRate(
                heartbeatCaptor.capture(), eq(10_000L), eq(10_000L), eq(TimeUnit.MILLISECONDS)
        );
        when(taskGuard.refresh(lock, ttl)).thenReturn(false);
        RedisSearchReindexLeaseAdapter adapter = new RedisSearchReindexLeaseAdapter(taskGuard, renewer, false);

        SearchReindexLeasePort.Lease lease = adapter.tryAcquire(ttl).orElseThrow();
        heartbeatCaptor.getValue().run();

        assertThat(lease.isValid()).isFalse();
        lease.close();
        verify(taskGuard).release(lock);
    }
}
