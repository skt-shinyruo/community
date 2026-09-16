package com.nowcoder.community.common.text;

/**
 * Shared unsafe-code-point table for credential text (usernames, login names).
 * Rejects invisible/confusable characters: control/format/surrogate types,
 * hangul fillers, variation selectors, musical format marks, and tag blocks.
 */
public final class UnsafeCodePoints {

    private UnsafeCodePoints() {
    }

    public static boolean containsUnsafe(String value) {
        return value != null && value.codePoints().anyMatch(UnsafeCodePoints::isUnsafe);
    }

    public static boolean isUnsafe(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE) {
            return true;
        }
        return codePoint == 0x034F
                || between(codePoint, 0x115F, 0x1160)
                || between(codePoint, 0x17B4, 0x17B5)
                || between(codePoint, 0x180B, 0x180F)
                || codePoint == 0x3164
                || between(codePoint, 0xFE00, 0xFE0F)
                || codePoint == 0xFFA0
                || between(codePoint, 0x1BCA0, 0x1BCA3)
                || between(codePoint, 0x1D173, 0x1D17A)
                || between(codePoint, 0xE0000, 0xE0FFF);
    }

    private static boolean between(int value, int lowerInclusive, int upperInclusive) {
        return value >= lowerInclusive && value <= upperInclusive;
    }
}
