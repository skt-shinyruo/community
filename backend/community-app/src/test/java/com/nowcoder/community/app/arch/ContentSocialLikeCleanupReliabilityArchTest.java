package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community.content",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ContentSocialLikeCleanupReliabilityArchTest {

    private static final String LEGACY_CLEANUP_API =
            "com.nowcoder.community.social.api.action.SocialLikeCleanupActionApi";

    @ArchTest
    static final ArchRule content_application_must_not_call_volatile_social_like_cleanup_api =
            noClasses()
                    .that().resideInAnyPackage("..content.application..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(LEGACY_CLEANUP_API)
                    .because("content deletion must publish a durable owner event; Social cleanup is an idempotent consumer");
}
