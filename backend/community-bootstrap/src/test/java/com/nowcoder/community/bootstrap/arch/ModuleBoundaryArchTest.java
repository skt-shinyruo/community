package com.nowcoder.community.bootstrap.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guardrails for the flattened monolith:
 * - Domain packages may depend on other domains only via stable entry packages ({@code ..<domain>.api..}, {@code ..<domain>.application..}, or {@code ..<domain>.event..}).
 * - Domain packages must not depend on bootstrap/app wiring packages, except the security-rule SPI used by domain-owned auth rules.
 *
 * <p>These tests are meant to prevent "big ball of mud" erosion as modules evolve.</p>
 */
public class ModuleBoundaryArchTest {

    private static final String ROOT = "com.nowcoder.community";

    private static final List<String> DOMAIN_MODULES = List.of(
            "auth",
            "user",
            "content",
            "social",
            "message",
            "search",
            "analytics",
            "ops"
    );

    @Test
    void domain_packages_must_only_depend_on_other_domains_entry_packages() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        List<String> violations = new ArrayList<>();

        for (JavaClass origin : classes) {
            String originDomain = domainOf(origin.getPackageName());
            if (originDomain == null) {
                continue;
            }

            for (Dependency dep : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                if (target == null) {
                    continue;
                }
                String targetPkg = target.getPackageName();
                if (targetPkg == null || !targetPkg.startsWith(ROOT + ".")) {
                    continue;
                }

                if (targetPkg.startsWith(ROOT + ".bootstrap.")
                        && !target.getName().equals(ROOT + ".bootstrap.security.ApiSecurityRules")) {
                    violations.add("[" + origin.getName() + "] -> [" + target.getName() + "] (" + dep.getDescription() + ")");
                    continue;
                }

                String targetDomain = domainOf(targetPkg);
                if (targetDomain == null || targetDomain.equals(originDomain)) {
                    continue;
                }

                String allowedApiPrefix = ROOT + "." + targetDomain + ".api";
                String allowedApplicationPrefix = ROOT + "." + targetDomain + ".application";
                String allowedEventPrefix = ROOT + "." + targetDomain + ".event";
                boolean allowed = targetPkg.startsWith(allowedApiPrefix + ".")
                        || targetPkg.equals(allowedApiPrefix)
                        || targetPkg.startsWith(allowedApplicationPrefix + ".")
                        || targetPkg.equals(allowedApplicationPrefix)
                        || targetPkg.startsWith(allowedEventPrefix + ".")
                        || targetPkg.equals(allowedEventPrefix);
                if (!allowed) {
                    violations.add("[" + origin.getName() + "] -> [" + target.getName() + "] (" + dep.getDescription() + ")");
                }
            }
        }

        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Cross-domain dependencies must go through the callee's api/application/event package only")
                .isEmpty();
    }

    private static String domainOf(String pkg) {
        if (pkg == null || !pkg.startsWith(ROOT + ".")) {
            return null;
        }
        for (String domain : DOMAIN_MODULES) {
            String prefix = ROOT + "." + domain + ".";
            if (pkg.startsWith(prefix) || pkg.equals(ROOT + "." + domain)) {
                return domain;
            }
        }
        return null;
    }
}
