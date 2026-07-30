package com.nowcoder.yierloom.testkit;

import java.util.List;

public final class PluginContractException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private final List<PluginViolation> violations;

    public PluginContractException(List<PluginViolation> violations) {
        super(message(violations));
        this.violations = List.copyOf(violations);
    }

    public List<PluginViolation> violations() {
        return violations;
    }

    private static String message(List<PluginViolation> violations) {
        String codes = violations.stream()
                .filter(violation -> violation.severity() == PluginViolationSeverity.ERROR)
                .map(PluginViolation::code)
                .distinct()
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("UNKNOWN");
        return "YierLoom plugin contract invalid: " + codes;
    }
}
