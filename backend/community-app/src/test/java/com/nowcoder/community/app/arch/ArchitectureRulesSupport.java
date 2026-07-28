package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ArchitectureRulesSupport {

    static final Set<String> CORE_DOMAINS = Set.of(
            "auth",
            "user",
            "profile",
            "interaction",
            "content",
            "social",
            "notice",
            "search",
            "analytics",
            "growth",
            "market",
            "wallet",
            "drive"
    );

    static final Set<String> ADAPTER_DOMAINS = Set.of(
            "ops",
            "im"
    );

    static final Set<String> PLATFORM_MODULES = Set.of(
            "runtime"
    );

    static final Set<String> TECHNICAL_ROOTS = Set.of(
            "app",
            "infra"
    );

    static final Set<String> BUSINESS_OR_ADAPTER_DOMAINS = Stream.concat(
            CORE_DOMAINS.stream(),
            ADAPTER_DOMAINS.stream()
    ).collect(Collectors.toUnmodifiableSet());

    static final Set<String> TACTICAL_ROOTS = Stream.of(
                    CORE_DOMAINS,
                    ADAPTER_DOMAINS,
                    PLATFORM_MODULES
            )
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    static final Set<String> CLASSIFIED_TOP_LEVEL_ROOTS = Stream.concat(
                    TACTICAL_ROOTS.stream(),
                    TECHNICAL_ROOTS.stream()
            )
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> TACTICAL_LAYERS = Set.of(
            "controller",
            "application",
            "domain",
            "infrastructure",
            "api",
            "contracts"
    );

    private static final String ROOT_PACKAGE = "com.nowcoder.community.";

    private ArchitectureRulesSupport() {
    }

    static Set<String> discoverTacticalRoots(Collection<String> packageNames) {
        Set<String> discovered = new TreeSet<>();
        for (String packageName : packageNames) {
            if (!packageName.startsWith(ROOT_PACKAGE)) {
                continue;
            }
            String relativePackage = packageName.substring(ROOT_PACKAGE.length());
            String[] segments = relativePackage.split("\\.");
            if (segments.length >= 2 && TACTICAL_LAYERS.contains(segments[1])) {
                discovered.add(segments[0]);
            }
        }
        return discovered;
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

    static boolean residesInPackagePrefixes(JavaClass javaClass, Set<String> packagePrefixes) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            return false;
        }
        int domainEnd = packageName.indexOf('.', ROOT_PACKAGE.length());
        if (domainEnd < 0 || domainEnd + 1 >= packageName.length()) {
            return false;
        }
        String remainder = packageName.substring(domainEnd + 1);
        for (String prefix : packagePrefixes) {
            if (remainder.equals(prefix) || remainder.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
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
            Set<String> layers
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isCoreDomain(item)) {
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
            Set<String> disallowedDomains
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isCoreDomain(item)) {
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
            Set<String> disallowedDomains
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
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
            boolean foreignCoreDomainOnly
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
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

    static ArchCondition<JavaClass> onlyDependOnForeignPackagePrefixes(
            String description,
            Set<String> allowedPackagePrefixes
    ) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isCoreDomain(item)) {
                    return;
                }
                String originDomain = domainOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetDomain = domainOf(target);
                    if (!CORE_DOMAINS.contains(targetDomain) || originDomain.equals(targetDomain)) {
                        continue;
                    }
                    if (residesInPackagePrefixes(target, allowedPackagePrefixes)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnForeignOwnerApiPackages() {
        return new ArchCondition<>("not depend on foreign owner api packages before application boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetDomain = domainOf(target);
                    if (originDomain.equals(targetDomain) || !CORE_DOMAINS.contains(targetDomain)) {
                        continue;
                    }
                    if (!residesInPackagePrefixes(target, Set.of("api.query", "api.action", "api.model"))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnForeignApplicationPackages() {
        return new ArchCondition<>("not depend on foreign application packages before owner application boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetDomain = domainOf(target);
                    if (originDomain.equals(targetDomain) || !CORE_DOMAINS.contains(targetDomain)) {
                        continue;
                    }
                    if (residesInLayer(target, Set.of("application"))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnSameDomainOwnerApiEntries() {
        return new ArchCondition<>("not depend on same-domain owner api query/action entries") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!originDomain.equals(domainOf(target))) {
                        continue;
                    }
                    if (!residesInPackagePrefixes(target, Set.of("api.query", "api.action"))) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnSameDomainServicesExceptApplicationServices() {
        return new ArchCondition<>("not depend on same-domain non-ApplicationService services or app packages") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!originDomain.equals(domainOf(target))) {
                        continue;
                    }
                    boolean sameDomainRawService = target.getSimpleName().endsWith("Service")
                            && !target.getSimpleName().endsWith("ApplicationService");
                    boolean sameDomainUseCaseOrAppPackage = residesInLayer(target, Set.of("app"));
                    if (sameDomainRawService || sameDomainUseCaseOrAppPackage) {
                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                    }
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnSameDomainInfrastructureBeforeApplicationService() {
        return new ArchCondition<>("not depend on same-domain infrastructure before ApplicationService boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (originDomain.equals(domainOf(target))
                            && residesInLayer(target, Set.of("infrastructure"))
                            && !sharesTopLevelOwner(item, target)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    static ArchCondition<JavaClass> notDependOnSameDomainDomainOrPersistenceBeforeApplicationService() {
        return new ArchCondition<>("not depend on same-domain domain implementation or persistence before ApplicationService boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!originDomain.equals(domainOf(target))) {
                        continue;
                    }
                    boolean domainImplementation = residesInPackagePrefixes(target, Set.of(
                            "domain.model",
                            "domain.repository",
                            "domain.service"
                    ));
                    boolean persistenceImplementation = residesInPackagePrefixes(target, Set.of(
                            "infrastructure.persistence"
                    ));
                    if (domainImplementation || persistenceImplementation) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
