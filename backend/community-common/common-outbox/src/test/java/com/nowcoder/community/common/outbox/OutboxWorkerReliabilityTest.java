package com.nowcoder.community.common.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxWorkerReliabilityTest {

    private static final String TOPIC = "projection.points";

    @Test
    void eachBatchCandidateShouldReceiveALeaseFromItsOwnClaimTime() {
        Instant pollTime = Instant.parse("2026-08-04T00:00:00Z");
        MutableClock clock = new MutableClock(pollTime);
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        properties.setBatchSize(2);
        properties.setProcessingLease(Duration.ofSeconds(30));

        OutboxEvent first = event(1, "event-1", 0);
        OutboxEvent second = event(2, "event-2", 0);
        OutboxLease firstLease = lease(1);
        OutboxLease secondLease = lease(2);
        AtomicInteger handled = new AtomicInteger();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public String topic() {
                return TOPIC;
            }

            @Override
            public void handle(OutboxEvent ignored) {
                handled.incrementAndGet();
                clock.advance(Duration.ofSeconds(20));
            }
        };

        when(store.findDuePending(2, pollTime)).thenReturn(List.of(first, second));
        when(store.tryClaimProcessing(any(UUID.class), any(Instant.class), any(Instant.class)))
                .thenReturn(Optional.of(firstLease), Optional.of(secondLease));
        when(store.findClaimedEvent(firstLease)).thenReturn(Optional.of(first));
        when(store.findClaimedEvent(secondLease)).thenReturn(Optional.of(second));
        when(store.markSucceeded(any(OutboxLease.class), any(Instant.class))).thenReturn(true);

        try (OutboxWorker worker = new OutboxWorker(store, Map.of(TOPIC, handler), properties, clock)) {
            assertThat(worker.pollOnce()).isEqualTo(2);
            assertThat(handled).hasValue(2);
        }

        InOrder calls = inOrder(store);
        calls.verify(store).recoverExpiredLeases(pollTime);
        calls.verify(store).findDuePending(2, pollTime);
        calls.verify(store).tryClaimProcessing(first.id(), pollTime.plusSeconds(30), pollTime);
        calls.verify(store).findClaimedEvent(firstLease);
        calls.verify(store).markSucceeded(firstLease, pollTime.plusSeconds(20));
        calls.verify(store).tryClaimProcessing(
                second.id(),
                pollTime.plusSeconds(50),
                pollTime.plusSeconds(20)
        );
        calls.verify(store).findClaimedEvent(secondLease);
        calls.verify(store).markSucceeded(secondLease, pollTime.plusSeconds(40));
    }

    @Test
    void missingHandlerShouldMoveEventToDeadAfterRetryBudgetIsExhausted() {
        Instant now = Instant.parse("2026-08-04T01:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        properties.setMaxRetries(2);
        OutboxEvent event = event(3, "missing-handler", 2);
        OutboxLease lease = lease(3);
        String error = "no handler for topic=" + TOPIC;

        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of(event));
        when(store.tryClaimProcessing(event.id(), now.plusSeconds(30), now)).thenReturn(Optional.of(lease));
        when(store.findClaimedEvent(lease)).thenReturn(Optional.of(event));
        when(store.markDead(lease, now, error)).thenReturn(true);

        try (OutboxWorker worker = new OutboxWorker(
                store,
                Map.of(),
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        )) {
            assertThat(worker.pollOnce()).isEqualTo(1);
        }
        verify(store).markDead(lease, now, error);
        verify(store, never()).markFailedAndScheduleRetry(
                any(OutboxLease.class),
                any(Instant.class),
                any(Instant.class),
                any(String.class)
        );
    }

    @Test
    void terminalFailureShouldImmediatelyDeadLetterAndScrubThePayload() {
        Instant now = Instant.parse("2026-08-04T01:30:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        OutboxEvent event = event(4, "expired-sensitive-mail", 0);
        OutboxLease lease = lease(4);
        OutboxTerminalException failure = new OutboxTerminalException(
                "delivery_expired", "mail expired before delivery");
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public String topic() {
                return TOPIC;
            }

            @Override
            public void handle(OutboxEvent ignored) {
                throw failure;
            }
        };

        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of(event));
        when(store.tryClaimProcessing(event.id(), now.plusSeconds(30), now)).thenReturn(Optional.of(lease));
        when(store.findClaimedEvent(lease)).thenReturn(Optional.of(event));
        when(store.markDeadAndScrubPayload(lease, now, failure.toString())).thenReturn(true);

        try (OutboxWorker worker = new OutboxWorker(
                store,
                Map.of(TOPIC, handler),
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        )) {
            assertThat(worker.pollOnce()).isEqualTo(1);
        }

        verify(store).markDeadAndScrubPayload(lease, now, failure.toString());
        verify(store, never()).markFailedAndScheduleRetry(
                any(OutboxLease.class), any(Instant.class), any(Instant.class), any(String.class));
        verify(store, never()).markSucceeded(any(OutboxLease.class), any(Instant.class));
    }

    @Test
    void schedulerShouldFailFastWhenTwoHandlersOwnTheSameNormalizedTopic() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
        OutboxHandler first = handler(TOPIC);
        OutboxHandler duplicate = handler("  " + TOPIC + "  ");
        when(handlersProvider.getIfAvailable()).thenReturn(List.of(first, duplicate));

        assertThatThrownBy(() -> new OutboxWorkerScheduler(
                store,
                handlersProvider,
                enabledProperties(),
                Clock.systemUTC()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate outbox handlers")
                .hasMessageContaining(TOPIC);
    }

    @Test
    void configuredRecoveryLimitShouldBePassedToTheBoundedStoreOperation() {
        Instant now = Instant.parse("2026-08-04T06:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        properties.setRecoverLimit(7);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of());

        try (OutboxWorker worker = new OutboxWorker(
                store,
                Map.of(),
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        )) {
            assertThat(worker.pollOnce()).isZero();
        }

        verify(store).recoverExpiredLeases(now, 7);
        verify(store, never()).recoverExpiredLeases(now);
    }

    @Test
    void heartbeatShouldPreventAnotherWorkerFromReplayingALongRunningHandler() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            createSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);
            assertThat(store.enqueue("long-running", TOPIC, "1", "{}")).isTrue();

            OutboxProperties properties = enabledProperties();
            properties.setProcessingLease(Duration.ofMillis(300));
            properties.setRecoverLimit(10);
            AtomicInteger executions = new AtomicInteger();
            OutboxHandler blockingHandler = new OutboxHandler() {
                @Override
                public String topic() {
                    return TOPIC;
                }

                @Override
                public void handle(OutboxEvent event) {
                    executions.incrementAndGet();
                    handlerEntered.countDown();
                    try {
                        if (!releaseHandler.await(3, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test handler was not released");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("test handler interrupted", interrupted);
                    }
                }
            };

            try (OutboxWorker first = new OutboxWorker(
                    store,
                    Map.of(TOPIC, blockingHandler),
                    properties,
                    Clock.systemUTC()
            ); OutboxWorker second = new OutboxWorker(
                    store,
                    Map.of(TOPIC, blockingHandler),
                    properties,
                    Clock.systemUTC()
            )) {
                Future<Integer> firstPoll = handlerExecutor.submit(first::pollOnce);
                assertThat(handlerEntered.await(2, TimeUnit.SECONDS)).isTrue();
                Instant firstDeadline = jdbcTemplate.queryForObject(
                        "select processing_lease_until from outbox_event where event_id = ?",
                        java.sql.Timestamp.class,
                        "long-running"
                ).toInstant();

                await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                    Instant currentDeadline = jdbcTemplate.queryForObject(
                            "select processing_lease_until from outbox_event where event_id = ?",
                            java.sql.Timestamp.class,
                            "long-running"
                    ).toInstant();
                    assertThat(currentDeadline).isAfter(firstDeadline);
                    assertThat(Instant.now()).isAfter(firstDeadline);
                });

                assertThat(second.pollOnce()).isZero();
                assertThat(executions).hasValue(1);

                releaseHandler.countDown();
                assertThat(firstPoll.get(2, TimeUnit.SECONDS)).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject(
                        "select status from outbox_event where event_id = ?",
                        String.class,
                        "long-running"
                )).isEqualTo(OutboxEventStatus.SUCCEEDED);
            }
        } finally {
            releaseHandler.countDown();
            handlerExecutor.shutdownNow();
            database.shutdown();
        }
    }

    private static OutboxProperties enabledProperties() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setBatchSize(10);
        properties.setProcessingLease(Duration.ofSeconds(30));
        properties.setMaxRetries(3);
        return properties;
    }

    private static OutboxEvent event(int seed, String eventId, int retryCount) {
        return new OutboxEvent(
                uuid(seed),
                eventId,
                TOPIC,
                Integer.toString(seed),
                "{}",
                OutboxEventStatus.PENDING,
                retryCount,
                null,
                null,
                null,
                null
        );
    }

    private static OutboxLease lease(int seed) {
        return new OutboxLease(uuid(seed), uuid(seed + 100));
    }

    private static UUID uuid(int seed) {
        return UUID.fromString("01965429-b34a-7000-8000-" + String.format("%012d", seed));
    }

    private static OutboxHandler handler(String topic) {
        return new OutboxHandler() {
            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void handle(OutboxEvent event) {
                // No-op: this handler is only used to validate scheduler registration.
            }
        };
    }

    private static void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                create table outbox_event (
                  id binary(16) primary key,
                  event_id varchar(64) not null unique,
                  topic varchar(255) not null,
                  event_key varchar(255) not null,
                  payload clob not null,
                  status varchar(32) not null,
                  lease_token binary(16),
                  processing_lease_until timestamp,
                  retry_count int not null default 0,
                  next_retry_at timestamp,
                  last_error varchar(512),
                  trace_id varchar(32),
                  traceparent varchar(128),
                  created_at timestamp default current_timestamp,
                  updated_at timestamp default current_timestamp
                )
                """);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
