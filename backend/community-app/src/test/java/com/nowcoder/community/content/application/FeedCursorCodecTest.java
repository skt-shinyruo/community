package com.nowcoder.community.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeedCursorCodecTest {

    private final FeedCursorCodec codec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void hotCursorShouldRoundTripStableDatabaseBoundary() {
        UUID postId = UUID.fromString("00000000-0000-7000-8000-000000000123");
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                1,
                42.75,
                new Date(1_754_000_000_123L),
                postId
        );

        FeedCursorCodec.CursorState decoded = codec.decode(codec.encodeHotPage(7, 20, boundary));
        String rawCursor = new String(
                java.util.Base64.getUrlDecoder().decode(codec.encodeHotPage(7, 20, boundary)),
                java.nio.charset.StandardCharsets.UTF_8
        );

        assertThat(decoded.page()).isEqualTo(7);
        assertThat(decoded.size()).isEqualTo(20);
        assertThat(decoded.hasHotBoundary()).isTrue();
        assertThat(decoded.hotBoundary()).isEqualTo(boundary);
        assertThat(rawCursor).contains("\"page\":7", "\"size\":20");
    }

    @Test
    void legacyPageCursorShouldRemainReadableWithoutDatabaseBoundary() {
        FeedCursorCodec.CursorState decoded = codec.decode(codec.encodePage(3, 10));

        assertThat(decoded.page()).isEqualTo(3);
        assertThat(decoded.size()).isEqualTo(10);
        assertThat(decoded.hasHotBoundary()).isFalse();
    }

    @Test
    void malformedHotCursorShouldResetToInitialState() {
        String malformed = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"version\":2,\"page\":1,\"size\":20,\"type\":0,"
                        + "\"score\":1,\"createTimeMillis\":123,\"postId\":\"bad-id\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(codec.decode(malformed)).isEqualTo(FeedCursorCodec.CursorState.initial());
    }

    @Test
    void forgedCursorOutsideServerBoundsShouldResetToInitialState() {
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"page\":2147483647,\"size\":2147483647}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(codec.decode(forged)).isEqualTo(FeedCursorCodec.CursorState.initial());
    }
}
