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
class DddLayeringArchTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..application..",
                            "..infrastructure..",
                            "org.springframework..",
                            "jakarta..",
                            "javax.."
                    );

    @ArchTest
    static final ArchRule application_must_not_depend_on_transport_or_persistence =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..infrastructure..",
                            "org.springframework.web..",
                            "org.springframework.http..",
                            "org.springframework.web.multipart..",
                            "jakarta.servlet.."
                    );

    @ArchTest
    static final ArchRule lifecycle_application_must_use_application_owned_delete_port =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .and().haveSimpleName("ObjectLifecycleApplicationService")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..infrastructure.storage..",
                            "..infrastructure.persistence.."
                    );

    @ArchTest
    static final ArchRule application_ports_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..application.port..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_domain_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain..",
                            "..infrastructure.."
                    );
}
