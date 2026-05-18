package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class CommentSideEffectBoundaryArchTest {

    @ArchTest
    static final ArchRule comment_create_must_not_synchronously_call_reward_or_growth_apis =
            noClasses()
                    .that().haveFullyQualifiedName("com.nowcoder.community.content.application.CommentApplicationService")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..user.api.action..",
                            "..user.api.model..",
                            "..growth.api.action..",
                            "..growth.api.model.."
                    )
                    .because("comment creation must publish content events and let reward/growth outbox handlers process side effects");
}
