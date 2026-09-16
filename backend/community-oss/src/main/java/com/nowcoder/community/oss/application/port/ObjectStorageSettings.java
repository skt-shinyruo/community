package com.nowcoder.community.oss.application.port;

public interface ObjectStorageSettings {

    /** Public base URL fallback; matches the local edge gateway (nginx) port. */
    String DEFAULT_PUBLIC_BASE_URL = "http://localhost:12880";

    String publicBaseUrl();

    String storageBucket();

    static String normalizePublicBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_PUBLIC_BASE_URL : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
