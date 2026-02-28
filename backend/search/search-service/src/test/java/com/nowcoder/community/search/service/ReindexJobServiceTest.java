package com.nowcoder.community.search.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexJobServiceTest {

    @Test
    void tryStartShouldAcquireAndSetJobKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("search:reindex:lock"), anyString(), eq(Duration.ofSeconds(30)))).thenReturn(true);

        ReindexJobService svc = new ReindexJobService(
                redisTemplate,
                mock(ScheduledExecutorService.class),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofSeconds(10)
        );

        ReindexJobService.ReindexJob job = svc.tryStart();
        assertNotNull(job);
        assertTrue(job.acquired());
        assertNotNull(job.jobId());

        verify(ops).setIfAbsent(eq("search:reindex:lock"), eq(job.jobId()), eq(Duration.ofSeconds(30)));
        verify(ops).set(eq("search:reindex:job"), eq(job.jobId()), eq(Duration.ofSeconds(120)));
    }

    @Test
    void tryStartWhenAlreadyLockedShouldReturnExistingJobId() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("search:reindex:lock"), anyString(), any(Duration.class))).thenReturn(false);
        when(ops.get("search:reindex:lock")).thenReturn("existing-job");

        ReindexJobService svc = new ReindexJobService(
                redisTemplate,
                mock(ScheduledExecutorService.class),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofSeconds(10)
        );

        ReindexJobService.ReindexJob job = svc.tryStart();
        assertNotNull(job);
        assertFalse(job.acquired());
        assertTrue("existing-job".equals(job.jobId()));
    }

    @Test
    void startRenewalShouldScheduleAndRenewWhenOwner() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler)
                .scheduleAtFixedRate(runnableCaptor.capture(), eq(1L), eq(1L), eq(TimeUnit.SECONDS));

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(1L);

        ReindexJobService svc = new ReindexJobService(
                redisTemplate,
                scheduler,
                Duration.ofSeconds(10),
                Duration.ofSeconds(120),
                Duration.ofSeconds(1)
        );

        AutoCloseable renewal = svc.startRenewal("job-1");
        assertNotNull(renewal);

        Runnable task = runnableCaptor.getValue();
        assertNotNull(task);
        task.run();

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("search:reindex:lock")),
                eq("job-1"),
                eq("10")
        );

        renewal.close();
        verify(future).cancel(false);
    }

    @Test
    void startRenewalShouldCancelFutureWhenOwnerLost() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<?> future = mock(ScheduledFuture.class);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler)
                .scheduleAtFixedRate(runnableCaptor.capture(), eq(1L), eq(1L), eq(TimeUnit.SECONDS));

        // renewIfOwner -> 0 : owner 丢失
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(0L);

        ReindexJobService svc = new ReindexJobService(
                redisTemplate,
                scheduler,
                Duration.ofSeconds(10),
                Duration.ofSeconds(120),
                Duration.ofSeconds(1)
        );

        svc.startRenewal("job-2");
        Runnable task = runnableCaptor.getValue();
        task.run();

        verify(future).cancel(false);
    }
}
