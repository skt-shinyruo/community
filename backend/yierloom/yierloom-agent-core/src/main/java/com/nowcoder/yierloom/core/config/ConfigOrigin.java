package com.nowcoder.yierloom.core.config;

import java.util.Objects;

public record ConfigOrigin(ConfigSource source, String location) {
    public ConfigOrigin {
        Objects.requireNonNull(source);
        Objects.requireNonNull(location);
    }
}
