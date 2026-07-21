package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PassthroughHotFeedProjectionGuardTest {

    private static final long EVENT_TTL_MILLIS = Duration.ofDays(7).toMillis();

    @Test
    void shouldAcceptValidSourceMetadata() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();

        HotFeedProjectionGuard.ProjectionAttempt attempt = guard.tryBegin(uuid(1), "evt-1", 1L, false);

        assertThat(attempt.accepted()).isTrue();
        assertThat(attempt.terminalDeletion()).isFalse();
        assertThat(guard.isCurrent(attempt)).isTrue();
    }

    @Test
    void shouldRejectInvalidSourceMetadata() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();

        assertThat(guard.tryBegin(uuid(1), " ", 1L, false).accepted()).isFalse();
        assertThat(guard.tryBegin(null, "evt-1", 1L, false).accepted()).isFalse();
        assertThat(guard.tryBegin(uuid(1), "evt-1", 0L, false).accepted()).isFalse();
    }

    @Test
    void committedTerminalDeletionShouldPermanentlySuppressOrdinaryEventsInProcess() {
        AtomicLong now = new AtomicLong(1_000L);
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard(now::get);

        HotFeedProjectionGuard.ProjectionAttempt ordinary = guard.tryBegin(uuid(2), "evt-normal-10", 10L, false);
        guard.commit(ordinary);
        HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(uuid(2), "evt-delete-5", 5L, true);
        assertThat(deletion.accepted()).isTrue();
        guard.commit(deletion);

        assertThat(guard.tryBegin(uuid(2), "evt-normal-4", 4L, false).accepted()).isFalse();
        assertThat(guard.tryBegin(uuid(2), "evt-normal-11", 11L, false).accepted()).isFalse();
        assertThat(guard.tryBegin(uuid(2), "evt-delete-5", 5L, true).accepted()).isFalse();

        now.addAndGet(EVENT_TTL_MILLIS + 1L);

        assertThat(guard.tryBegin(uuid(2), "evt-normal-after-event-expiry", 11L, false).accepted()).isFalse();
        assertThat(guard.tryBegin(uuid(2), "evt-delete-5", 5L, true).accepted()).isFalse();
    }

    @Test
    void committedEventIdentityShouldExpireAtSevenDaysWhileVersionRemainsPermanent() {
        AtomicLong now = new AtomicLong(10_000L);
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard(now::get);
        HotFeedProjectionGuard.ProjectionAttempt first = guard.tryBegin(uuid(5), "evt-expiring", 10L, false);
        guard.commit(first);
        HotFeedProjectionGuard.ProjectionAttempt second = guard.tryBegin(uuid(6), "evt-expiring", 10L, false);
        guard.commit(second);

        now.addAndGet(EVENT_TTL_MILLIS - 1L);

        assertThat(guard.tryBegin(uuid(5), "evt-expiring", 10L, false).accepted()).isFalse();

        now.incrementAndGet();

        HotFeedProjectionGuard.ProjectionAttempt replayAtExpiry =
                guard.tryBegin(uuid(5), "evt-expiring", 10L, false);
        assertThat(replayAtExpiry.accepted()).isTrue();
        guard.abort(replayAtExpiry);

        now.incrementAndGet();

        HotFeedProjectionGuard.ProjectionAttempt replayAfterExpiry =
                guard.tryBegin(uuid(6), "evt-expiring", 10L, false);
        assertThat(replayAfterExpiry.accepted()).isTrue();
        guard.abort(replayAfterExpiry);
        assertThat(guard.tryBegin(uuid(5), "evt-older-version", 9L, false).accepted()).isFalse();
    }

    @Test
    void tryBeginShouldDrainExpiredEventIdentitiesFromTheExpiryQueue() {
        AtomicLong now = new AtomicLong(20_000L);
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard(now::get);
        guard.commit(guard.tryBegin(uuid(7), "evt-seven", 1L, false));

        now.addAndGet(Duration.ofSeconds(30).toMillis());

        guard.commit(guard.tryBegin(uuid(8), "evt-eight", 1L, false));
        assertThat(committedEventExpirations(guard)).hasSize(2);
        assertThat(committedEventExpiryQueue(guard)).hasSize(2);

        now.addAndGet(EVENT_TTL_MILLIS - Duration.ofSeconds(30).toMillis());

        HotFeedProjectionGuard.ProjectionAttempt firstCleanup =
                guard.tryBegin(uuid(9), "evt-nine", 1L, false);
        guard.abort(firstCleanup);
        assertThat(committedEventExpirations(guard)).hasSize(1);
        assertThat(committedEventExpiryQueue(guard)).hasSize(1);

        now.addAndGet(Duration.ofSeconds(30).toMillis());

        HotFeedProjectionGuard.ProjectionAttempt secondCleanup =
                guard.tryBegin(uuid(10), "evt-ten", 1L, false);
        guard.abort(secondCleanup);
        assertThat(committedEventExpirations(guard)).isEmpty();
        assertThat(committedEventExpiryQueue(guard)).isEmpty();
    }

    @Test
    void abortedTerminalDeletionShouldNotSuppressLaterProjection() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();

        HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(uuid(3), "evt-delete-abort", 5L, true);
        guard.abort(deletion);

        assertThat(guard.tryBegin(uuid(3), "evt-normal-6", 6L, false).accepted()).isTrue();
    }

    @Test
    void unownedCommitShouldFailInsteadOfSilentlyLosingTerminalState() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();
        HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(uuid(4), "evt-delete", 5L, true);
        HotFeedProjectionGuard.ProjectionAttempt unowned = HotFeedProjectionGuard.ProjectionAttempt.accepted(
                uuid(4),
                "evt-delete",
                5L,
                true,
                "not-the-owner"
        );

        assertThatThrownBy(() -> guard.commit(unowned))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hot feed projection commit lost lease");

        guard.abort(deletion);
        assertThat(guard.tryBegin(uuid(4), "evt-normal", 6L, false).accepted()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Long> committedEventExpirations(PassthroughHotFeedProjectionGuard guard) {
        return (Map<Object, Long>) ReflectionTestUtils.getField(guard, "committedEventExpirations");
    }

    private static Collection<?> committedEventExpiryQueue(PassthroughHotFeedProjectionGuard guard) {
        return (Collection<?>) ReflectionTestUtils.getField(guard, "committedEventExpiryQueue");
    }
}
