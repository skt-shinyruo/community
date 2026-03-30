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
                            "..notice.service..",
                            "..notice.event..",
                            "..notice.mapper.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..message.controller..",
                            "..message.service..",
                            "..message.event..",
                            "..message.mapper.."
                    );
}
