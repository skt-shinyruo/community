package com.nowcoder.community.notice;

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
    static final ArchRule notice_infrastructure_persistence_must_only_depend_on_persistence_contracts =
            classes()
                    .that().resideInAnyPackage("..notice.infrastructure.persistence..")
                    .should(notDependOnNoticeAdaptersOrApplicationUseCases())
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notDependOnNoticeAdaptersOrApplicationUseCases() {
        return new ArchCondition<>("not depend on notice adapters or application use cases") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String packageName = target.getPackageName();
                    if (!packageName.startsWith("com.nowcoder.community.notice.")) {
                        continue;
                    }
                    if (target.getFullName().equals("com.nowcoder.community.notice.application.NoticeProjectionEventRecorder")) {
                        continue;
                    }
                    if (packageName.startsWith("com.nowcoder.community.notice.controller")
                            || packageName.startsWith("com.nowcoder.community.notice.application")
                            || packageName.startsWith("com.nowcoder.community.notice.dto")
                            || packageName.startsWith("com.nowcoder.community.notice.service")
                            || packageName.startsWith("com.nowcoder.community.notice.event")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
