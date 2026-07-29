package com.nowcoder.yierloom.core.config;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApiVersion {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private ApiVersion() {
    }

    public static boolean isCompatible(String agentVersion, String requiredVersion) {
        Version agent = parse(agentVersion);
        Version required = parse(requiredVersion);
        return agent.major() == required.major() && agent.minor() >= required.minor();
    }

    private static Version parse(String value) {
        Matcher matcher = VERSION.matcher(Objects.requireNonNull(value, "version"));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid API version");
        }
        try {
            return new Version(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid API version", exception);
        }
    }

    private record Version(int major, int minor, int patch) {
    }
}
