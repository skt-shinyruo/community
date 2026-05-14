package com.nowcoder.community.common.observability.logging;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RuntimeLogSanitizer {

    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern HEX_PATH_SEGMENT = Pattern.compile("(?i)^[0-9a-f]{16,}$");
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("^\\d+$");

    private RuntimeLogSanitizer() {
    }

    public static String text(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value.toString();
        if (text.isBlank()) {
            return "-";
        }
        return text.replaceAll("[\\r\\n\\t]+", " ");
    }

    public static String operation(String value) {
        String text = text(value).trim().toLowerCase(Locale.ROOT);
        if (text.length() > 64) {
            text = text.substring(0, 64);
        }
        return text.replaceAll("[^a-z0-9_.:-]+", "_");
    }

    public static String uppercaseOperation(String value) {
        return operation(value).toUpperCase(Locale.ROOT);
    }

    public static String pathOnly(String uri) {
        if (uri == null || uri.isBlank()) {
            return "-";
        }
        try {
            URI parsed = URI.create(uri);
            return parsed.getPath() == null || parsed.getPath().isBlank() ? "/" : parsed.getPath();
        } catch (IllegalArgumentException ex) {
            int query = uri.indexOf('?');
            String path = query >= 0 ? uri.substring(0, query) : uri;
            int scheme = path.indexOf("://");
            if (scheme >= 0) {
                int slash = path.indexOf('/', scheme + 3);
                path = slash >= 0 ? path.substring(slash) : "/";
            }
            return path.isBlank() ? "/" : path;
        }
    }

    public static String pathTemplate(String uri) {
        String path = pathOnly(uri);
        if ("-".equals(path) || "/".equals(path)) {
            return path;
        }
        if (path.startsWith("/files/")) {
            return "/files/{objectKey}";
        }
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (isLikelyIdentifier(segments[i])) {
                segments[i] = "{id}";
            }
        }
        return String.join("/", segments);
    }

    private static boolean isLikelyIdentifier(String segment) {
        return NUMERIC_PATH_SEGMENT.matcher(segment).matches()
                || UUID_PATH_SEGMENT.matcher(segment).matches()
                || HEX_PATH_SEGMENT.matcher(segment).matches();
    }

    public static String sizeBucket(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        if (bytes < 1024) {
            return "<1KB";
        }
        if (bytes < 1024L * 1024L) {
            return "1KB-1MB";
        }
        if (bytes < 10L * 1024L * 1024L) {
            return "1MB-10MB";
        }
        return "10MB+";
    }

    public static String rowBucket(long rows) {
        if (rows < 0) {
            return "unknown";
        }
        if (rows == 0) {
            return "0";
        }
        if (rows == 1) {
            return "1";
        }
        if (rows <= 10) {
            return "2-10";
        }
        if (rows <= 100) {
            return "11-100";
        }
        if (rows <= 1000) {
            return "101-1000";
        }
        return "1000+";
    }

    public static String sqlOperation(String sql) {
        if (sql == null) {
            return "unknown";
        }
        String trimmed = sql.stripLeading().toLowerCase(Locale.ROOT);
        for (String operation : new String[]{"select", "insert", "update", "delete", "merge", "replace"}) {
            if (trimmed.startsWith(operation)) {
                return operation;
            }
        }
        return "unknown";
    }

    public static long percent(long used, long max) {
        if (max <= 0) {
            return 0;
        }
        return used * 100 / max;
    }
}
