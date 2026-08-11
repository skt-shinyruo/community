package com.nowcoder.community.social.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowCursorCodecTest {

    private final FollowCursorCodec codec = new FollowCursorCodec();

    @Test
    void cursorShouldRoundTripStableBoundary() {
        FollowCursorCodec.Boundary boundary = new FollowCursorCodec.Boundary(
                Instant.parse("2026-08-10T12:34:56Z"),
                uuid(42)
        );

        assertThat(codec.decode(codec.encode(boundary.followTime(), boundary.targetId())))
                .contains(boundary);
    }

    @Test
    void malformedCursorShouldBeRejected() {
        assertThatThrownBy(() -> codec.decode("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid follow cursor");
    }
}
