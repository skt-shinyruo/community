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
class DtoBoundaryArchTest {

    @ArchTest
    static final ArchRule dto_must_not_depend_on_entities =
            noClasses()
                    .that().resideInAnyPackage("..dto..")
                    .should().dependOnClassesThat().resideInAnyPackage("..entity..");

    @ArchTest
    static final ArchRule dto_must_not_depend_on_mappers_or_daos =
            noClasses()
                    .that().resideInAnyPackage("..dto..")
                    .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..dao..");

    @ArchTest
    static final ArchRule non_controller_layers_must_not_depend_on_http_response_dtos =
            classes()
                    .that().resideInAnyPackage("..application..", "..domain..", "..infrastructure..")
                    .should(notDependOnHttpResponseDtos());

    private static ArchCondition<JavaClass> notDependOnHttpResponseDtos() {
        return new ArchCondition<>("not depend on HTTP response DTOs") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getPackageName().contains(".dto")) {
                        continue;
                    }
                    if (!target.getSimpleName().endsWith("Response")) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                }
            }
        };
    }
}
