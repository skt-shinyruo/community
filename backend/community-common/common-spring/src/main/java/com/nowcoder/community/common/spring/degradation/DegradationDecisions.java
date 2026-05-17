package com.nowcoder.community.common.spring.degradation;

import java.util.Map;
import java.util.Set;

public class DegradationDecisions {

    private static final String STRICT = "strict";
    private static final Set<String> ALLOWED_MODES = Set.of("off", "read-only", "best-effort", STRICT);

    private final DegradationProperties properties;

    public DegradationDecisions(DegradationProperties properties) {
        this.properties = properties;
    }

    public String mode(String key) {
        if (key == null || key.isBlank() || properties == null) {
            return STRICT;
        }

        Map<String, String> modes = properties.getModes();
        if (modes == null) {
            return STRICT;
        }

        String mode = modes.get(key);
        if (mode == null) {
            return STRICT;
        }
        return ALLOWED_MODES.contains(mode) ? mode : STRICT;
    }
}
