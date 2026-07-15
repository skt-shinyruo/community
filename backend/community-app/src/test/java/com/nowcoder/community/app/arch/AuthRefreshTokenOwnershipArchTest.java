package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.nowcoder.community", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthRefreshTokenOwnershipArchTest {

    @ArchTest
    static final ArchRule auth_infrastructure_must_not_depend_on_user_api = noClasses()
            .that().resideInAnyPackage("..auth.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..user.api..")
            .because("refresh token persistence is owned by auth and must not be delegated through user api");

    @ArchTest
    static final ArchRule user_refresh_token_session_types_must_be_retired = noClasses()
            .that().resideInAnyPackage(
                    "..user.application..",
                    "..user.domain..",
                    "..user.api..",
                    "..user.infrastructure.."
            )
            .should().haveSimpleNameContaining("RefreshTokenSession")
            .because("refresh token session application, domain, API, and persistence types belong to auth");
}
