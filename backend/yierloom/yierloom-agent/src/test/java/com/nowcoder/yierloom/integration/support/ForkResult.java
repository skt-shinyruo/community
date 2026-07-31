package com.nowcoder.yierloom.integration.support;

import java.util.Objects;

public record ForkResult(int exitCode, String stdout, String stderr) {
    public ForkResult {
        stdout = Objects.requireNonNull(stdout, "stdout");
        stderr = Objects.requireNonNull(stderr, "stderr");
    }

    public String combinedOutput() {
        if (stdout.isEmpty()) {
            return stderr;
        }
        if (stderr.isEmpty()) {
            return stdout;
        }
        return stdout + System.lineSeparator() + stderr;
    }
}
