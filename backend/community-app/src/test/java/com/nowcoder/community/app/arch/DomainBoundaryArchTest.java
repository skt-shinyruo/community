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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainBoundaryArchTest {

    private static String domainOf(JavaClass javaClass) {
        // Treat the first package segment after com.nowcoder.community as the domain boundary.
        String packageName = javaClass.getPackageName();
        String prefix = "com.nowcoder.community.";
        if (!packageName.startsWith(prefix)) {
            return "";
        }
        int domainStart = prefix.length();
        int domainEnd = packageName.indexOf('.', domainStart);
        if (domainEnd < 0) {
            return packageName.substring(domainStart);
        }
        return packageName.substring(domainStart, domainEnd);
    }

    private static boolean isTrackedEntity(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.matches("com\\.nowcoder\\.community\\.[^.]+\\.entity(\\..*)?");
    }

    private static ArchCondition<JavaClass> notDependOnForeignEntities() {
        return new ArchCondition<>("not depend on foreign entities") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (isTrackedEntity(target)
                            && !originDomain.equals(domainOf(target))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_foreign_entities =
            classes()
                    .that().resideInAnyPackage("..controller..")
                    .should(notDependOnForeignEntities());

    @ArchTest
    static final ArchRule non_user_code_must_not_depend_on_user_internals =
            noClasses()
                    .that().resideOutsideOfPackage("..user..")
                    .should().dependOnClassesThat().resideInAnyPackage("..user.service..", "..user.entity..");

    @ArchTest
    static final ArchRule new_collaboration_classes_must_not_use_facade_naming =
            classes()
                    .should().haveSimpleNameNotEndingWith("FacadeService");
}
