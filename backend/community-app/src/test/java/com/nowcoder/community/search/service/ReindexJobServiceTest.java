package com.nowcoder.community.search.service;

import com.nowcoder.community.infra.scheduler.SingleFlightTaskGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexJobServiceTest {

    @Test
    void tryStartShouldReturnAcquiredJobWhenDistributedLockSucceeds() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));
        ReindexJobService.ReindexJob job = service.tryStart();

        assertThat(job.acquired()).isTrue();
        assertThat(job.jobId()).isNotBlank();
        assertThat(job.lock()).isEqualTo(lock);
    }

    @Test
    void finishShouldReleaseDistributedLockFromJobHandle() {
        SingleFlightTaskGuard guard = mock(SingleFlightTaskGuard.class);
        SingleFlightTaskGuard.Lock lock = new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1");
        when(guard.tryAcquire("search:reindex", Duration.ofMinutes(30))).thenReturn(lock);

        ReindexJobService service = new ReindexJobService(guard, Duration.ofMinutes(30));
        ReindexJobService.ReindexJob job = service.tryStart();

        service.finish(job);

        verify(guard).release(lock);
    }

    @Test
    void startRenewalShouldRefreshDistributedLockUsingCompareAndPexpireScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SingleFlightTaskGuard guard = new SingleFlightTaskGuard(redisTemplate);
        Duration ttl = Duration.ofMinutes(30);
        ReindexJobService service = new ReindexJobService(guard, ttl);
        ReindexJobService.ReindexJob job = new ReindexJobService.ReindexJob(
                "job-1",
                true,
                new SingleFlightTaskGuard.Lock("sf:task:search:reindex", "token-1")
        );

        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of("sf:task:search:reindex")),
                org.mockito.ArgumentMatchers.eq("token-1"),
                org.mockito.ArgumentMatchers.eq(Long.toString(ttl.toMillis()))
        )).thenReturn(1L);

        try (ReindexJobService.RenewalHandle ignored = service.startRenewal(job)) {
            // best-effort: the handle is responsible for lifecycle
        }

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(List.of("sf:task:search:reindex")),
                org.mockito.ArgumentMatchers.eq("token-1"),
                org.mockito.ArgumentMatchers.eq(Long.toString(ttl.toMillis()))
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('get', KEYS[1]) == ARGV[1]");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire', KEYS[1], ARGV[2])");
    }
}
