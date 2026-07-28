package com.nowcoder.yierloom.api;

import java.util.regex.Pattern;

public record PluginDescriptor(
        String id,
        String name,
        String version,
        String apiVersion,
        boolean defaultEnabled,
        int order
) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    public PluginDescriptor {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid plugin id");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("plugin name must not be blank");
        }
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("invalid plugin version");
        }
        if (apiVersion == null || !VERSION.matcher(apiVersion).matches()) {
            throw new IllegalArgumentException("invalid API version");
        }
    }
}
