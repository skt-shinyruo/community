package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

final class ArchitectureRulesSupport {

    static final Set<String> CORE_DOMAINS = Set.of(
            "auth",
            "user",
            "content",
            "social",
            "message",
            "search",
            "analytics",
            "growth"
    );

    static final Set<String> MIGRATION_BASELINE_FOREIGN_IMPLEMENTATION_CALLERS = Set.of();
    static final Set<String> MIGRATION_BASELINE_CONTROLLER_MAPPER_CALLERS = Set.of();
    static final Set<String> MIGRATION_BASELINE_CONTROLLER_FOREIGN_ENTITY_CALLERS = Set.of();
    static final Set<String> MIGRATION_BASELINE_CONTROLLER_ENTITY_CALLERS = Set.of();

    private static final String ROOT_PACKAGE = "com.nowcoder.community.";

    private ArchitectureRulesSupport() {
    }

    static String domainOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            return "";
        }
        int domainStart = ROOT_PACKAGE.length();
        int domainEnd = packageName.indexOf('.', domainStart);
        if (domainEnd < 0) {
            return packageName.substring(domainStart);
        }
        return packageName.substring(domainStart, domainEnd);
    }

    static boolean isCoreDomain(JavaClass javaClass) {
        return CORE_DOMAINS.contains(domainOf(javaClass));
    }

    static boolean residesInLayer(JavaClass javaClass, Set<String> layers) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            return false;
        }
        int domainEnd = packageName.indexOf('.', ROOT_PACKAGE.length());
        if (domainEnd < 0 || domainEnd + 1 >= packageName.length()) {
            return false;
        }
        String remainder = packageName.substring(domainEnd + 1);
        for (String layer : layers) {
            if (remainder.equals(layer) || remainder.startsWith(layer + ".")) {
                return true;
            }
        }
        return false;
    }

    static boolean isWhitelisted(JavaClass javaClass, Set<String> whitelist) {
        return whitelist.contains(javaClass.getFullName());
    }

    static boolean sharesTopLevelOwner(JavaClass left, JavaClass right) {
        return topLevelName(left).equals(topLevelName(right));
    }

    private static String topLevelName(JavaClass javaClass) {
        String fullName = javaClass.getFullName();
        int nestedIndex = fullName.indexOf('$');
        if (nestedIndex < 0) {
            return fullName;
        }
        return fullName.substring(0, nestedIndex);
    }

    static ArchCondition<JavaClass> notDependOnForeignCoreLayers(
            String description,
            Set<String> layers,
            Set<String> legacyOriginWhitelist
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isCoreDomain(item) || isWhitelisted(item, legacyOriginWhitelist)) {
                    return;
                }
                String originDomain = domainOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetDomain = domainOf(target);
                    if (!CORE_DOMAINS.contains(targetDomain) || originDomain.equals(targetDomain)) {
                        continue;
                    }
                    if (!residesInLayer(target, layers)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnDomainsFromCoreOrigins(
            String description,
            Set<String> disallowedDomains,
            Set<String> legacyOriginWhitelist
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isCoreDomain(item) || isWhitelisted(item, legacyOriginWhitelist)) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!disallowedDomains.contains(domainOf(target))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnDomains(
            String description,
            Set<String> disallowedDomains,
            Set<String> legacyOriginWhitelist
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (isWhitelisted(item, legacyOriginWhitelist)) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!disallowedDomains.contains(domainOf(target))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnLayers(
            String description,
            Set<String> layers,
            boolean foreignCoreDomainOnly,
            Set<String> legacyOriginWhitelist
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (isWhitelisted(item, legacyOriginWhitelist)) {
                    return;
                }
                String originDomain = domainOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (sharesTopLevelOwner(item, target)) {
                        continue;
                    }
                    if (!residesInLayer(target, layers)) {
                        continue;
                    }
                    if (foreignCoreDomainOnly) {
                        String targetDomain = domainOf(target);
                        if (!CORE_DOMAINS.contains(targetDomain) || originDomain.equals(targetDomain)) {
                            continue;
                        }
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }
}
