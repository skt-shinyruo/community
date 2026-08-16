package com.nowcoder.community.common.logging;

import java.util.Locale;

public final class EventLogMessage {

    private EventLogMessage() {
    }

    public static String format(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Event keyValues must contain key/value pairs");
        }
        StringBuilder message = new StringBuilder(160);
        for (int i = 0; i < keyValues.length; i += 2) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append(keyValues[i]).append('=').append(encode(keyValues[i + 1]));
        }
        return message.toString();
    }

    private static String encode(Object value) {
        if (value == null || String.valueOf(value).isEmpty()) {
            return "-";
        }
        String raw = String.valueOf(value);
        StringBuilder encoded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)
                    || isInvisibleFormattingCharacter(ch) || ch == '=' || ch == '%') {
                encoded.append('%');
                String hex = Integer.toHexString(ch).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            } else {
                encoded.append(ch);
            }
        }
        return encoded.toString();
    }

    private static boolean isInvisibleFormattingCharacter(char value) {
        int type = Character.getType(value);
        return type == Character.FORMAT
                || type == Character.SURROGATE
                || value == '\u034F'
                || between(value, '\u115F', '\u1160')
                || between(value, '\u17B4', '\u17B5')
                || between(value, '\u180B', '\u180F')
                || value == '\u3164'
                || between(value, '\uFE00', '\uFE0F')
                || value == '\uFFA0';
    }

    private static boolean between(char value, char lowerInclusive, char upperInclusive) {
        return value >= lowerInclusive && value <= upperInclusive;
    }
}
