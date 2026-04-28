package com.nowcoder.community.notice;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NoticeModuleArchTest {

    @ArchTest
    static final ArchRule notice_runtime_must_not_depend_on_legacy_message_runtime_packages =
            noClasses()
                    .that().resideInAnyPackage(
                            "..notice.controller..",
                            "..notice.application..",
                            "..notice.infrastructure.event..",
                            "..notice.infrastructure.persistence.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..message.controller..",
                            "..message.service..",
                            "..message.event..",
                            "..message.mapper.."
                    );

    @ArchTest
    static final ArchRule notice_controllers_must_enter_application_only =
            noClasses()
                    .that().resideInAnyPackage("..notice.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.service..",
                            "..notice.mapper..",
                            "..notice.entity..",
                            "..notice.infrastructure..",
                            "..notice.domain.."
                    );

    @ArchTest
    static final ArchRule notice_event_adapters_must_enter_application_only =
            noClasses()
                    .that().resideInAnyPackage("..notice.infrastructure.event..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.service..",
                            "..notice.mapper..",
                            "..notice.entity..",
                            "..notice.controller.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_application_must_not_depend_on_transport_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..notice.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.controller..",
                            "..notice.dto..",
                            "..notice.mapper..",
                            "..notice.entity..",
                            "..notice.infrastructure.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_infrastructure_persistence_must_implement_domain_repository =
            noClasses()
                    .that().resideInAnyPackage("..notice.infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.controller..",
                            "..notice.application..",
                            "..notice.dto..",
                            "..notice.service..",
                            "..notice.event.."
                    )
                    .allowEmptyShould(true);
}
