package com.nowcoder.community.content.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class FeedCursorCodec {

    public String encodePage(int page, int size) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((
                        "{\"page\":" + Math.max(0, page) + ",\"size\":" + Math.max(1, size) + "}"
                ).getBytes(StandardCharsets.UTF_8));
    }

    public CursorState decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return CursorState.initial();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return new CursorState(extractNumber(json, "page", 0), extractNumber(json, "size", 0));
        } catch (IllegalArgumentException ex) {
            return CursorState.initial();
        }
    }

    private int extractNumber(String json, String field, int fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        String pattern = "\"" + field + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return fallback;
        }
        int from = start + pattern.length();
        int end = from;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == from) {
            return fallback;
        }
        try {
            return Integer.parseInt(json.substring(from, end));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public record CursorState(int page, int size) {
        public CursorState {
            page = Math.max(0, page);
            size = Math.max(0, size);
        }

        public static CursorState initial() {
            return new CursorState(0, 0);
        }
    }
}
