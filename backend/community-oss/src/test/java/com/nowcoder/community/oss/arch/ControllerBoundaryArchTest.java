package com.nowcoder.community.oss.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community.oss",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ControllerBoundaryArchTest {

    @ArchTest
    static final ArchRule controllers_must_enter_through_application_services_only =
            noClasses()
                    .that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain..",
                            "..infrastructure.."
                    );
}
