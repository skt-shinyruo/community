package com.nowcoder.community.im.core.application;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationCursorCodecTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final ConversationCursorCodec codec = new ConversationCursorCodec(jsonCodec);

    @Test
    void shouldRoundTripNanosecondSafeConversationBoundary() {
        Instant sortAt = Instant.parse("2026-07-21T01:02:03.123456789Z");
        String conversationId = "00000000-0000-7000-8000-000000000001_00000000-0000-7000-8000-000000000002";

        String cursor = codec.encode(sortAt, conversationId);

        assertThat(codec.decode(cursor))
                .contains(new ConversationCursorCodec.Cursor(1, sortAt, conversationId));
    }

    @Test
    void shouldTreatBlankCursorAsFirstPage() {
        assertThat(codec.decode(null)).isEqualTo(Optional.empty());
        assertThat(codec.decode("")).isEqualTo(Optional.empty());
        assertThat(codec.decode("  ")).isEqualTo(Optional.empty());
    }

    @Test
    void shouldRejectMalformedCursorPayloads() {
        Map<String, Object> missingTime = payload(1, "2026-07-21T01:02:03Z", "conversation-1");
        missingTime.remove("sortAt");

        for (String cursor : new String[]{
                "%%%",
                encodeJson("{not-json"),
                encodePayload(payload(2, "2026-07-21T01:02:03Z", "conversation-1")),
                encodePayload(payload(1, "2026-07-21T01:02:03Z", "  ")),
                encodePayload(missingTime)
        }) {
            assertInvalid(cursor);
        }
    }

    private Map<String, Object> payload(Object version, Object sortAt, Object conversationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("sortAt", sortAt);
        payload.put("conversationId", conversationId);
        return payload;
    }

    private String encodePayload(Map<String, Object> payload) {
        return encodeJson(jsonCodec.toJson(payload));
    }

    private String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertInvalid(String cursor) {
        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT));
    }
}
