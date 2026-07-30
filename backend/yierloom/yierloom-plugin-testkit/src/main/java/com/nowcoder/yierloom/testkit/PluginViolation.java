package com.nowcoder.yierloom.testkit;

import java.util.Objects;

public record PluginViolation(
        PluginViolationSeverity severity,
        String code,
        String location,
        String detail
) {
    public PluginViolation {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        location = location == null ? "" : location;
        detail = detail == null ? "" : detail;
    }
}
