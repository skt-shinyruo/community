package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.nowcoder.community.";
    private static final Pattern ENTITY_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.entity(\\..*)?");
    private static final Pattern MAPPER_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.mapper(\\..*)?");
    private static final Pattern SERVICE_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.service(\\..*)?");

    private static final Set<String> LEGACY_FOREIGN_ENTITY_CALLERS = Set.of();
    private static final Set<String> LEGACY_FOREIGN_MAPPER_CALLERS = Set.of();
    private static final Set<String> LEGACY_FOREIGN_SERVICE_CALLERS = Set.of();
    private static final Set<String> LEGACY_FACADE_SERVICE_CLASSES = Set.of();

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_entities =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("entities", ENTITY_PACKAGE, LEGACY_FOREIGN_ENTITY_CALLERS));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_mappers =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("mappers", MAPPER_PACKAGE, LEGACY_FOREIGN_MAPPER_CALLERS));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_services =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("services", SERVICE_PACKAGE, LEGACY_FOREIGN_SERVICE_CALLERS));

    @ArchTest
    static final ArchRule production_classes_must_not_end_with_facade_service =
            classes().should(notUseFacadeServiceNaming());

    @ArchTest
    static final ArchRule content_api_must_not_depend_on_content_legacy_transport_or_event_payloads =
            noClasses()
                    .that().resideInAnyPackage("..content.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..content.dto..", "..content.event.payload..")
                    .because("content.api is the synchronous collaboration boundary and must not leak DTOs or event payloads");

    private static ArchCondition<JavaClass> notDependOnForeignPackage(
            String packageLabel,
            Pattern trackedPackage,
            Set<String> legacyCallers
    ) {
        return new ArchCondition<>("not depend on foreign " + packageLabel) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (legacyCallers.contains(item.getName())) {
                    return;
                }
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (trackedPackage.matcher(target.getPackageName()).matches()
                            && !originDomain.equals(domainOf(target))) {
                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notUseFacadeServiceNaming() {
        return new ArchCondition<>("not use FacadeService suffix") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.getSimpleName().endsWith("FacadeService")
                        && !LEGACY_FACADE_SERVICE_CLASSES.contains(item.getName())) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " ends with FacadeService"
                    ));
                }
            }
        };
    }

    private static String domainOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return "";
        }
        int domainStart = BASE_PACKAGE.length();
        int domainEnd = packageName.indexOf('.', domainStart);
        if (domainEnd < 0) {
            return packageName.substring(domainStart);
        }
        return packageName.substring(domainStart, domainEnd);
    }
}
