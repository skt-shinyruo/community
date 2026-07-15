package com.nowcoder.community.content.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostMediaReferenceStateTest {

    private static final Date CREATED_AT = at("2026-07-15T01:00:00Z");
    private static final Date BIND_REQUESTED_AT = at("2026-07-15T01:01:00Z");
    private static final Date BOUND_AT = at("2026-07-15T01:02:00Z");
    private static final Date RELEASE_REQUESTED_AT = at("2026-07-15T01:03:00Z");
    private static final Date RELEASED_AT = at("2026-07-15T01:04:00Z");

    @Test
    void referenceStateShouldMoveThroughDurableDesiredStateSequence() {
        PostMediaReferenceState unbound = new PostMediaReferenceState(
                PostMediaReferenceStatus.UNBOUND,
                0L,
                CREATED_AT
        );

        PostMediaReferenceState bindPending = unbound.requestBind(BIND_REQUESTED_AT);
        PostMediaReferenceState bound = bindPending.markBound(1L, BOUND_AT);
        PostMediaReferenceState releasePending = bound.requestRelease(RELEASE_REQUESTED_AT);
        PostMediaReferenceState released = releasePending.markReleased(2L, RELEASED_AT);

        assertThat(bindPending).isEqualTo(new PostMediaReferenceState(
                PostMediaReferenceStatus.BIND_PENDING,
                1L,
                BIND_REQUESTED_AT
        ));
        assertThat(bound).isEqualTo(new PostMediaReferenceState(
                PostMediaReferenceStatus.BOUND,
                1L,
                BOUND_AT
        ));
        assertThat(releasePending).isEqualTo(new PostMediaReferenceState(
                PostMediaReferenceStatus.RELEASE_PENDING,
                2L,
                RELEASE_REQUESTED_AT
        ));
        assertThat(released).isEqualTo(new PostMediaReferenceState(
                PostMediaReferenceStatus.RELEASED,
                2L,
                RELEASED_AT
        ));
    }

    @Test
    void finalizeShouldRejectAStaleOperationVersion() {
        PostMediaReferenceState bindPending = new PostMediaReferenceState(
                PostMediaReferenceStatus.BIND_PENDING,
                7L,
                BIND_REQUESTED_AT
        );
        PostMediaReferenceState releasePending = new PostMediaReferenceState(
                PostMediaReferenceStatus.RELEASE_PENDING,
                8L,
                RELEASE_REQUESTED_AT
        );

        assertThatThrownBy(() -> bindPending.markBound(6L, BOUND_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");
        assertThatThrownBy(() -> releasePending.markReleased(7L, RELEASED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");
    }

    @Test
    void finalizeShouldRejectTheWrongPendingState() {
        PostMediaReferenceState unbound = new PostMediaReferenceState(
                PostMediaReferenceStatus.UNBOUND,
                0L,
                CREATED_AT
        );

        assertThatThrownBy(() -> unbound.markBound(0L, BOUND_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BIND_PENDING");
        assertThatThrownBy(() -> unbound.markReleased(0L, RELEASED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASE_PENDING");
    }

    private static Date at(String value) {
        return Date.from(Instant.parse(value));
    }
}
