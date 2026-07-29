package com.nowcoder.yierloom.core.plugin;

import java.nio.file.Path;
import java.util.Objects;

public record PluginIssue(Path sourcePath, String reasonCode) {
    public PluginIssue {
        sourcePath = Objects.requireNonNull(sourcePath, "sourcePath").normalize();
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
    }

    public String summary() {
        return "source=" + sourcePath + ", reason=" + reasonCode;
    }
}
