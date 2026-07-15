package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.nowcoder.community", importOptions = ImportOption.DoNotIncludeTests.class)
class LikeInteractionBoundaryArchTest {

    @ArchTest
    static final ArchRule social_like_application_must_not_call_foreign_owner_apis = noClasses()
            .that().haveFullyQualifiedName(
                    "com.nowcoder.community.social.application.LikeApplicationService"
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..content.api..",
                    "..user.api.."
            )
            .because("interaction resolves foreign owners before entering the social like use case");

    @ArchTest
    static final ArchRule social_like_application_must_not_use_legacy_content_resolver = noClasses()
            .that().haveFullyQualifiedName(
                    "com.nowcoder.community.social.application.LikeApplicationService"
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nowcoder.community.social.application.ContentEntityResolver"
            )
            .because("resolved target data must enter social through its published action contract");
}
