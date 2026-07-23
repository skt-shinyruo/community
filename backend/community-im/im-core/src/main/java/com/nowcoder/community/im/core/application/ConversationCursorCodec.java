package com.nowcoder.community.im.core.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@Component
public class ConversationCursorCodec {

    private static final int VERSION = 1;
    private static final String INVALID_CURSOR_MESSAGE = "会话游标非法";
    private static final Set<String> PAYLOAD_FIELDS = Set.of("version", "sortAt", "conversationId");

    private final JsonCodec jsonCodec;

    public ConversationCursorCodec(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encode(Instant sortAt, String conversationId) {
        if (sortAt == null || !StringUtils.hasText(conversationId)) {
            throw invalidCursor();
        }
        Payload payload = new Payload(VERSION, sortAt.toString(), conversationId);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonCodec.toJson(payload).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Cursor> decode(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            JsonNode payload = jsonCodec.readTree(json);
            validatePayloadShape(payload);

            JsonNode version = payload.get("version");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.asInt() != VERSION) {
                throw invalidCursor();
            }

            Instant sortAt = Instant.parse(requiredText(payload, "sortAt"));
            String conversationId = requiredText(payload, "conversationId");
            return Optional.of(new Cursor(VERSION, sortAt, conversationId));
        } catch (BusinessException error) {
            throw error;
        } catch (IllegalArgumentException | DateTimeException | JsonCodecException ignored) {
            throw invalidCursor();
        }
    }

    private void validatePayloadShape(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.size() != PAYLOAD_FIELDS.size()) {
            throw invalidCursor();
        }
        for (String field : PAYLOAD_FIELDS) {
            if (!payload.has(field)) {
                throw invalidCursor();
            }
        }
    }

    private String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) {
            throw invalidCursor();
        }
        return value.textValue();
    }

    private BusinessException invalidCursor() {
        return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, INVALID_CURSOR_MESSAGE);
    }

    public record Cursor(int version, Instant sortAt, String conversationId) {
    }

    private record Payload(int version, String sortAt, String conversationId) {
    }
}
