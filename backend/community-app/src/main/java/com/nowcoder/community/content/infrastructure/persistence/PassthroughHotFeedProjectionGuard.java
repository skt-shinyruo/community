package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.LongSupplier;

@Repository
@ConditionalOnMissingBean(HotFeedProjectionGuard.class)
public class PassthroughHotFeedProjectionGuard implements HotFeedProjectionGuard {

    private static final int LOCK_ATTEMPTS = 20;
    private static final long LOCK_BACKOFF_MILLIS = 25L;
    private static final long EVENT_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L;

    private final Set<UUID> terminallyDeletedPostIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<EventIdentity, Long> committedEventExpirations = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ExpiringEventIdentity> committedEventExpiryQueue =
            new PriorityBlockingQueue<>();
    private final ConcurrentMap<UUID, Long> committedVersions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> activeTokens = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    public PassthroughHotFeedProjectionGuard() {
        this(System::currentTimeMillis);
    }

    PassthroughHotFeedProjectionGuard(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    public ProjectionAttempt tryBegin(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion
    ) {
        if (postId == null || !StringUtils.hasText(sourceEventId) || sourceVersion <= 0L) {
            return ProjectionAttempt.rejected(postId, sourceEventId, sourceVersion, terminalDeletion);
        }
        String normalizedEventId = sourceEventId.trim();
        String token = UUID.randomUUID().toString();
        removeExpiredCommittedEvents(currentTimeMillis.getAsLong());
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            long now = currentTimeMillis.getAsLong();
            if (shouldReject(postId, normalizedEventId, sourceVersion, terminalDeletion, now)) {
                return ProjectionAttempt.rejected(postId, normalizedEventId, sourceVersion, terminalDeletion);
            }
            if (activeTokens.putIfAbsent(postId, token) == null) {
                long lockedNow = currentTimeMillis.getAsLong();
                if (shouldReject(postId, normalizedEventId, sourceVersion, terminalDeletion, lockedNow)) {
                    activeTokens.remove(postId, token);
                    return ProjectionAttempt.rejected(postId, normalizedEventId, sourceVersion, terminalDeletion);
                }
                return ProjectionAttempt.accepted(
                        postId,
                        normalizedEventId,
                        sourceVersion,
                        terminalDeletion,
                        token
                );
            }
            sleepBeforeRetry();
        }
        throw new IllegalStateException("hot feed projection lock busy: postId=" + postId);
    }

    @Override
    public boolean isCurrent(ProjectionAttempt attempt) {
        if (attempt == null
                || !attempt.accepted()
                || !attempt.token().equals(activeTokens.get(attempt.postId()))) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        return !shouldReject(
                attempt.postId(),
                attempt.sourceEventId(),
                attempt.sourceVersion(),
                attempt.terminalDeletion(),
                now
        );
    }

    @Override
    public void commit(ProjectionAttempt attempt) {
        if (attempt == null
                || !attempt.accepted()) {
            return;
        }
        if (!attempt.token().equals(activeTokens.get(attempt.postId()))) {
            throw new IllegalStateException(
                    "hot feed projection commit lost lease: postId=" + attempt.postId()
                            + ", sourceEventId=" + attempt.sourceEventId());
        }
        try {
            long now = currentTimeMillis.getAsLong();
            if (shouldReject(
                    attempt.postId(),
                    attempt.sourceEventId(),
                    attempt.sourceVersion(),
                    attempt.terminalDeletion(),
                    now
            )) {
                return;
            }
            committedVersions.merge(attempt.postId(), attempt.sourceVersion(), Math::max);
            EventIdentity eventIdentity = new EventIdentity(attempt.postId(), attempt.sourceEventId());
            long expiresAt = now + EVENT_TTL_MILLIS;
            committedEventExpirations.put(eventIdentity, expiresAt);
            committedEventExpiryQueue.offer(new ExpiringEventIdentity(eventIdentity, expiresAt));
            if (attempt.terminalDeletion()) {
                terminallyDeletedPostIds.add(attempt.postId());
            }
        } finally {
            activeTokens.remove(attempt.postId(), attempt.token());
        }
    }

    @Override
    public void abort(ProjectionAttempt attempt) {
        if (attempt != null && attempt.postId() != null && StringUtils.hasText(attempt.token())) {
            activeTokens.remove(attempt.postId(), attempt.token());
        }
    }

    private boolean shouldReject(
            UUID postId,
            String sourceEventId,
            long sourceVersion,
            boolean terminalDeletion,
            long now
    ) {
        if (terminallyDeletedPostIds.contains(postId)) {
            return true;
        }
        EventIdentity eventIdentity = new EventIdentity(postId, sourceEventId);
        Long eventExpiresAt = committedEventExpirations.get(eventIdentity);
        if (eventExpiresAt != null && eventExpiresAt > now) {
            return true;
        }
        if (eventExpiresAt != null) {
            committedEventExpirations.remove(eventIdentity, eventExpiresAt);
        }
        Long committedVersion = committedVersions.get(postId);
        return !terminalDeletion && committedVersion != null && committedVersion > sourceVersion;
    }

    private void removeExpiredCommittedEvents(long now) {
        synchronized (committedEventExpiryQueue) {
            ExpiringEventIdentity next = committedEventExpiryQueue.peek();
            while (next != null && next.expiresAt() <= now) {
                ExpiringEventIdentity expired = committedEventExpiryQueue.poll();
                if (expired != null) {
                    committedEventExpirations.remove(expired.eventIdentity(), expired.expiresAt());
                }
                next = committedEventExpiryQueue.peek();
            }
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(LOCK_BACKOFF_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("hot feed projection lock wait interrupted", e);
        }
    }

    private record EventIdentity(UUID postId, String sourceEventId) {
    }

    private record ExpiringEventIdentity(EventIdentity eventIdentity, long expiresAt)
            implements Comparable<ExpiringEventIdentity> {

        @Override
        public int compareTo(ExpiringEventIdentity other) {
            return Long.compare(expiresAt, other.expiresAt);
        }
    }
}
