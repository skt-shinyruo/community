package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class PassthroughHotFeedProjectionGuardTest {

    @Test
    void shouldAcceptValidSourceMetadata() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();

        HotFeedProjectionGuard.ProjectionAttempt attempt = guard.tryBegin(uuid(1), "evt-1", 1L);

        assertThat(attempt.accepted()).isTrue();
        assertThat(guard.isCurrent(attempt)).isTrue();
    }

    @Test
    void shouldRejectInvalidSourceMetadata() {
        PassthroughHotFeedProjectionGuard guard = new PassthroughHotFeedProjectionGuard();

        assertThat(guard.tryBegin(uuid(1), " ", 1L).accepted()).isFalse();
        assertThat(guard.tryBegin(null, "evt-1", 1L).accepted()).isFalse();
        assertThat(guard.tryBegin(uuid(1), "evt-1", 0L).accepted()).isFalse();
    }
}
