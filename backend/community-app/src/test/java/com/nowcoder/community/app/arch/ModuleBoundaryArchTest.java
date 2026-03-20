package com.nowcoder.community.app.arch;

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
 * - Domain packages may collaborate with other domains via service/entity/dto/event style entry points.
 * - Cross-domain dependencies must not reach the callee's controller/api web surface, application layer, or persistence internals.
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

                if (targetPkg.startsWith(ROOT + ".app.")
                        && !target.getName().equals(ROOT + ".app.security.ApiSecurityRules")) {
                    violations.add("[" + origin.getName() + "] -> [" + target.getName() + "] (" + dep.getDescription() + ")");
                    continue;
                }

                String targetDomain = domainOf(targetPkg);
                if (targetDomain == null || targetDomain.equals(originDomain)) {
                    continue;
                }

                if (isDisallowedCrossDomainTarget(targetDomain, target, targetPkg)) {
                    violations.add("[" + origin.getName() + "] -> [" + target.getName() + "] (" + dep.getDescription() + ")");
                }
            }
        }

        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Cross-domain dependencies must not reach web, application, or persistence internals")
                .isEmpty();
    }

    private static boolean isDisallowedCrossDomainTarget(String targetDomain, JavaClass target, String targetPkg) {
        String domainRoot = ROOT + "." + targetDomain;
        if (isPackageOrSubpackage(targetPkg, domainRoot + ".application")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".dao")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".mapper")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".repo")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".repository")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".api.internal")
                || isPackageOrSubpackage(targetPkg, domainRoot + ".controller")) {
            return true;
        }

        String apiPrefix = domainRoot + ".api";
        if (isPackageOrSubpackage(targetPkg, apiPrefix)) {
            return !isAllowedApiTarget(targetPkg, target);
        }
        return false;
    }

    private static boolean isAllowedApiTarget(String targetPkg, JavaClass target) {
        return hasPackageSegment(targetPkg, "dto")
                || hasPackageSegment(targetPkg, "event")
                || hasPackageSegment(targetPkg, "security")
                || target.getSimpleName().endsWith("ErrorCode");
    }

    private static boolean isPackageOrSubpackage(String pkg, String prefix) {
        return pkg.equals(prefix) || pkg.startsWith(prefix + ".");
    }

    private static boolean hasPackageSegment(String pkg, String segment) {
        return pkg.equals(ROOT + "." + segment)
                || pkg.endsWith("." + segment)
                || pkg.contains("." + segment + ".");
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
