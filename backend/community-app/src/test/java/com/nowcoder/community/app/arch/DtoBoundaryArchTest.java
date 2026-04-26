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

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DtoBoundaryArchTest {

    // Existing service DTO dependencies outside this refactor. Remove entries as each service moves to model results.
    private static final Set<String> LEGACY_SERVICE_RESPONSE_DTO_CALLERS = Set.of(
            "com.nowcoder.community.auth.service.AuthApplicationService",
            "com.nowcoder.community.auth.service.RegistrationService",
            "com.nowcoder.community.auth.service.RegistrationVerificationService",
            "com.nowcoder.community.content.service.BookmarkApplicationService",
            "com.nowcoder.community.content.service.CategoryApplicationService",
            "com.nowcoder.community.content.service.CategoryService",
            "com.nowcoder.community.content.service.ModerationApplicationService",
            "com.nowcoder.community.content.service.ModerationService",
            "com.nowcoder.community.content.service.TagApplicationService",
            "com.nowcoder.community.content.service.TagService",
            "com.nowcoder.community.growth.service.UserLevelService",
            "com.nowcoder.community.notice.service.NoticeApplicationService",
            "com.nowcoder.community.notice.service.NoticeService",
            "com.nowcoder.community.search.service.SearchAdminService",
            "com.nowcoder.community.social.service.LikeApplicationService",
            "com.nowcoder.community.user.service.AdminUserApplicationService",
            "com.nowcoder.community.user.service.AdminUserService",
            "com.nowcoder.community.user.service.AvatarService",
            "com.nowcoder.community.user.service.AvatarStorageProvider",
            "com.nowcoder.community.user.service.LocalAvatarStorageProvider",
            "com.nowcoder.community.user.service.R2AvatarStorageProvider",
            "com.nowcoder.community.user.service.UserAvatarApplicationService",
            "com.nowcoder.community.user.service.UserReadApplicationService",
            "com.nowcoder.community.wallet.service.WalletApplicationService",
            "com.nowcoder.community.wallet.service.WalletQueryService"
    );

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
    static final ArchRule services_must_not_depend_on_http_response_dtos =
            classes()
                    .that().resideInAnyPackage("..service..")
                    .should(notDependOnHttpResponseDtos());

    private static ArchCondition<JavaClass> notDependOnHttpResponseDtos() {
        return new ArchCondition<>("not depend on HTTP response DTOs") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (LEGACY_SERVICE_RESPONSE_DTO_CALLERS.contains(item.getName())) {
                    return;
                }
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
