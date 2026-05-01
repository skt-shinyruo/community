package com.nowcoder.community.auth.application.result;

public record RefreshCookieSpec(
        String name,
        String value,
        boolean httpOnly,
        boolean secure,
        String path,
        String sameSite,
        long maxAgeSeconds
) {
    public RefreshCookieSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("cookie name must not be blank");
        }
        value = value == null ? "" : value;
        path = path == null || path.isBlank() ? "/" : path;
        sameSite = sameSite == null ? "" : sameSite;
        maxAgeSeconds = Math.max(0, maxAgeSeconds);
    }
}
