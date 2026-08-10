package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ApplicationQueryBoundaryArchTest {

    private static final Set<String> FORBIDDEN_QUERY_CONTRACT_PREFIXES = Set.of(
            "com.nowcoder.community.common.idempotency.",
            "com.nowcoder.community.common.kafka.",
            "com.nowcoder.community.common.outbox.",
            "com.nowcoder.community.common.web.",
            "jakarta.servlet.",
            "org.apache.kafka.",
            "org.mybatis.",
            "org.springframework."
    );

    @ArchTest
    static final ArchRule application_query_entries_must_be_interfaces =
            classes().should(beAnInterfaceWhenApplicationQuery());

    @ArchTest
    static final ArchRule application_query_contracts_must_stay_pure =
            classes().should(beAPureApplicationQueryContract());

    @ArchTest
    static final ArchRule application_query_implementations_must_be_owner_infrastructure_adapters =
            classes().should(implementApplicationQueriesOnlyInOwnerInfrastructure());

    private static ArchCondition<JavaClass> beAPureApplicationQueryContract() {
        return new ArchCondition<>("keep application *Query contracts free of orchestration and transport dependencies") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!isApplicationQuery(item)) {
                    return;
                }
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.ABSTRACT)
                            || method.isAnnotatedWith(Transactional.class)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                method.getFullName() + " must be abstract and non-transactional"
                        ));
                    }
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (ArchitectureRulesSupport.sharesTopLevelOwner(item, target)) {
                        continue;
                    }
                    if (isForbiddenContractDependency(item, target)) {
                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beAnInterfaceWhenApplicationQuery() {
        return new ArchCondition<>("make top-level application *Query entries interfaces") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (isApplicationQuery(item) && !item.isInterface()) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getFullName() + " is a top-level application *Query but is not an interface"
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> implementApplicationQueriesOnlyInOwnerInfrastructure() {
        return new ArchCondition<>("implement application *Query contracts in same-owner infrastructure") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.isInterface() || item.getModifiers().contains(JavaModifier.ABSTRACT)) {
                    return;
                }
                item.getAllRawInterfaces().stream()
                        .filter(ApplicationQueryBoundaryArchTest::isApplicationQuery)
                        .forEach(query -> {
                            boolean sameOwner = ArchitectureRulesSupport.domainOf(item)
                                    .equals(ArchitectureRulesSupport.domainOf(query));
                            boolean infrastructure = ArchitectureRulesSupport.residesInLayer(
                                    item,
                                    Set.of("infrastructure")
                            );
                            if (!sameOwner || !infrastructure) {
                                events.add(SimpleConditionEvent.violated(
                                        item,
                                        item.getFullName() + " implements " + query.getFullName()
                                                + " outside same-owner infrastructure"
                                ));
                            }
                        });
            }
        };
    }

    private static boolean isForbiddenContractDependency(JavaClass query, JavaClass target) {
        String targetName = target.getFullName();
        if (FORBIDDEN_QUERY_CONTRACT_PREFIXES.stream().anyMatch(targetName::startsWith)) {
            return true;
        }
        String targetOwner = ArchitectureRulesSupport.domainOf(target);
        if (targetOwner.isEmpty()) {
            return false;
        }
        String queryOwner = ArchitectureRulesSupport.domainOf(query);
        if (!queryOwner.equals(targetOwner)) {
            return ArchitectureRulesSupport.TACTICAL_ROOTS.contains(targetOwner);
        }
        return ArchitectureRulesSupport.residesInLayer(
                target,
                Set.of("controller", "domain", "infrastructure", "api", "contracts")
        );
    }

    private static boolean isApplicationQuery(JavaClass javaClass) {
        String owner = ArchitectureRulesSupport.domainOf(javaClass);
        return !owner.isEmpty()
                && !javaClass.getFullName().contains("$")
                && javaClass.getPackageName().equals("com.nowcoder.community." + owner + ".application")
                && javaClass.getSimpleName().endsWith("Query");
    }
}
