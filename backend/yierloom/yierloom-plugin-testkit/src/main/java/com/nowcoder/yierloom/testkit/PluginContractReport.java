package com.nowcoder.yierloom.testkit;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PluginContractReport(List<PluginViolation> violations) {
    private static final Comparator<PluginViolation> ORDER = Comparator
            .comparing(PluginViolation::severity)
            .thenComparing(PluginViolation::code)
            .thenComparing(PluginViolation::location)
            .thenComparing(PluginViolation::detail);

    public PluginContractReport {
        Objects.requireNonNull(violations, "violations");
        violations = violations.stream()
                .map(violation -> Objects.requireNonNull(violation, "violation"))
                .sorted(ORDER)
                .toList();
    }

    public boolean valid() {
        return violations.stream()
                .noneMatch(violation -> violation.severity() == PluginViolationSeverity.ERROR);
    }

    public void throwIfInvalid() {
        if (!valid()) {
            throw new PluginContractException(violations);
        }
    }
}
