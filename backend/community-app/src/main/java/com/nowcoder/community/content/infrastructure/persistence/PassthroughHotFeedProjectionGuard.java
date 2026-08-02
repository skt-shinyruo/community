package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import com.nowcoder.community.content.application.PostProjectionVersionLane;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.LongSupplier;

@Repository
@ConditionalOnMissingBean(HotFeedProjectionGuard.class)
public class PassthroughHotFeedProjectionGuard implements HotFeedProjectionGuard {

    private static final int LOCK_ATTEMPTS = 20;
    private static final int MAX_EXPIRY_CLEANUP_PER_BEGIN = 256;
    private static final long LOCK_BACKOFF_MILLIS = 25L;
    private static final long EVENT_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L;

    private final ConcurrentMap<UUID, Long> terminalDeletionExpirations = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ExpiringTerminalDeletion> terminalDeletionExpiryQueue =
            new PriorityBlockingQueue<>();
    private final ConcurrentMap<EventIdentity, Long> committedEventExpirations = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ExpiringEventIdentity> committedEventExpiryQueue =
            new PriorityBlockingQueue<>();
    private final ConcurrentMap<VersionIdentity, VersionState> committedVersions = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<ExpiringVersionIdentity> committedVersionExpiryQueue =
            new PriorityBlockingQueue<>();
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
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion
    ) {
        if (postId == null
                || !StringUtils.hasText(sourceEventId)
                || sourceVersion <= 0L
                || sourceVersionLane == null) {
            return ProjectionAttempt.rejected(
                    postId,
                    sourceEventId,
                    sourceVersion,
                    sourceVersionLane,
                    terminalDeletion
            );
        }
        String normalizedEventId = sourceEventId.trim();
        String token = UUID.randomUUID().toString();
        removeExpiredState(currentTimeMillis.getAsLong());
        for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
            long now = currentTimeMillis.getAsLong();
            if (shouldReject(postId, normalizedEventId, sourceVersion, sourceVersionLane, terminalDeletion, now)) {
                return ProjectionAttempt.rejected(
                        postId,
                        normalizedEventId,
                        sourceVersion,
                        sourceVersionLane,
                        terminalDeletion
                );
            }
            if (activeTokens.putIfAbsent(postId, token) == null) {
                long lockedNow = currentTimeMillis.getAsLong();
                if (shouldReject(
                        postId,
                        normalizedEventId,
                        sourceVersion,
                        sourceVersionLane,
                        terminalDeletion,
                        lockedNow
                )) {
                    activeTokens.remove(postId, token);
                    return ProjectionAttempt.rejected(
                            postId,
                            normalizedEventId,
                            sourceVersion,
                            sourceVersionLane,
                            terminalDeletion
                    );
                }
                return ProjectionAttempt.accepted(
                        postId,
                        normalizedEventId,
                        sourceVersion,
                        sourceVersionLane,
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
                attempt.sourceVersionLane(),
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
                    attempt.sourceVersionLane(),
                    attempt.terminalDeletion(),
                    now
            )) {
                return;
            }
            long expiresAt = now + EVENT_TTL_MILLIS;
            if (attempt.sourceVersionLane().hasMonotonicSourceVersion()) {
                VersionIdentity versionIdentity = new VersionIdentity(
                        attempt.postId(),
                        attempt.sourceVersionLane()
                );
                VersionState versionState = committedVersions.compute(versionIdentity, (ignored, current) ->
                        new VersionState(
                                current == null
                                        ? attempt.sourceVersion()
                                        : Math.max(current.version(), attempt.sourceVersion()),
                                expiresAt
                        )
                );
                committedVersionExpiryQueue.offer(new ExpiringVersionIdentity(versionIdentity, versionState));
            }
            EventIdentity eventIdentity = new EventIdentity(attempt.postId(), attempt.sourceEventId());
            committedEventExpirations.put(eventIdentity, expiresAt);
            committedEventExpiryQueue.offer(new ExpiringEventIdentity(eventIdentity, expiresAt));
            if (attempt.terminalDeletion()) {
                terminalDeletionExpirations.put(attempt.postId(), expiresAt);
                terminalDeletionExpiryQueue.offer(new ExpiringTerminalDeletion(attempt.postId(), expiresAt));
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
            PostProjectionVersionLane sourceVersionLane,
            boolean terminalDeletion,
            long now
    ) {
        Long terminalExpiresAt = terminalDeletionExpirations.get(postId);
        if (terminalExpiresAt != null) {
            if (terminalExpiresAt > now) {
                return true;
            }
            terminalDeletionExpirations.remove(postId, terminalExpiresAt);
        }
        EventIdentity eventIdentity = new EventIdentity(postId, sourceEventId);
        Long eventExpiresAt = committedEventExpirations.get(eventIdentity);
        if (eventExpiresAt != null && eventExpiresAt > now) {
            return true;
        }
        if (eventExpiresAt != null) {
            committedEventExpirations.remove(eventIdentity, eventExpiresAt);
        }
        VersionIdentity versionIdentity = new VersionIdentity(postId, sourceVersionLane);
        VersionState committedVersion = committedVersions.get(versionIdentity);
        if (committedVersion != null && committedVersion.expiresAt() <= now) {
            committedVersions.remove(versionIdentity, committedVersion);
            committedVersion = null;
        }
        return !terminalDeletion
                && sourceVersionLane.hasMonotonicSourceVersion()
                && committedVersion != null
                && committedVersion.version() > sourceVersion;
    }

    private void removeExpiredState(long now) {
        removeExpiredCommittedEvents(now);
        removeExpiredCommittedVersions(now);
        removeExpiredTerminalDeletions(now);
    }

    private void removeExpiredCommittedEvents(long now) {
        synchronized (committedEventExpiryQueue) {
            ExpiringEventIdentity next = committedEventExpiryQueue.peek();
            int cleaned = 0;
            while (next != null
                    && next.expiresAt() <= now
                    && cleaned < MAX_EXPIRY_CLEANUP_PER_BEGIN) {
                ExpiringEventIdentity expired = committedEventExpiryQueue.poll();
                if (expired != null) {
                    committedEventExpirations.remove(expired.eventIdentity(), expired.expiresAt());
                    cleaned++;
                }
                next = committedEventExpiryQueue.peek();
            }
        }
    }

    private void removeExpiredCommittedVersions(long now) {
        synchronized (committedVersionExpiryQueue) {
            ExpiringVersionIdentity next = committedVersionExpiryQueue.peek();
            int cleaned = 0;
            while (next != null
                    && next.state().expiresAt() <= now
                    && cleaned < MAX_EXPIRY_CLEANUP_PER_BEGIN) {
                ExpiringVersionIdentity expired = committedVersionExpiryQueue.poll();
                if (expired != null) {
                    committedVersions.remove(expired.versionIdentity(), expired.state());
                    cleaned++;
                }
                next = committedVersionExpiryQueue.peek();
            }
        }
    }

    private void removeExpiredTerminalDeletions(long now) {
        synchronized (terminalDeletionExpiryQueue) {
            ExpiringTerminalDeletion next = terminalDeletionExpiryQueue.peek();
            int cleaned = 0;
            while (next != null
                    && next.expiresAt() <= now
                    && cleaned < MAX_EXPIRY_CLEANUP_PER_BEGIN) {
                ExpiringTerminalDeletion expired = terminalDeletionExpiryQueue.poll();
                if (expired != null) {
                    terminalDeletionExpirations.remove(expired.postId(), expired.expiresAt());
                    cleaned++;
                }
                next = terminalDeletionExpiryQueue.peek();
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

    private record VersionIdentity(UUID postId, PostProjectionVersionLane lane) {
    }

    private record VersionState(long version, long expiresAt) {
    }

    private record ExpiringEventIdentity(EventIdentity eventIdentity, long expiresAt)
            implements Comparable<ExpiringEventIdentity> {

        @Override
        public int compareTo(ExpiringEventIdentity other) {
            return Long.compare(expiresAt, other.expiresAt);
        }
    }

    private record ExpiringVersionIdentity(VersionIdentity versionIdentity, VersionState state)
            implements Comparable<ExpiringVersionIdentity> {

        @Override
        public int compareTo(ExpiringVersionIdentity other) {
            return Long.compare(state.expiresAt(), other.state.expiresAt());
        }
    }

    private record ExpiringTerminalDeletion(UUID postId, long expiresAt)
            implements Comparable<ExpiringTerminalDeletion> {

        @Override
        public int compareTo(ExpiringTerminalDeletion other) {
            return Long.compare(expiresAt, other.expiresAt);
        }
    }
}
