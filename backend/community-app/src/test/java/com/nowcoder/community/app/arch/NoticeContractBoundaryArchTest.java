package com.nowcoder.community.app.arch;

import com.nowcoder.community.notice.application.command.ProjectNoticeCommand;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community.notice",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NoticeContractBoundaryArchTest {

    @ArchTest
    static final ArchRule notice_application_and_domain_must_not_depend_on_foreign_event_contracts =
            noClasses()
                    .that().resideInAnyPackage("..notice.application..", "..notice.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..content.contracts.event..",
                            "..social.contracts.event..",
                            "..user.contracts.event..");

    @ArchTest
    static final ArchRule notice_commands_and_domain_models_must_not_declare_object_fields =
            fields()
                    .that().areDeclaredInClassesThat().resideInAnyPackage(
                            "..notice.application.command..",
                            "..notice.domain.model..")
                    .should().notHaveRawType(Object.class);

    @Test
    void projectionCommandMustBeAClosedNoticeOwnedHierarchy() {
        assertThat(ProjectNoticeCommand.class.isSealed()).isTrue();
        assertThat(ProjectNoticeCommand.class.getPermittedSubclasses()).hasSize(5);
    }
}
