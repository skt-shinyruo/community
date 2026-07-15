package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.nowcoder.community", importOptions = ImportOption.DoNotIncludeTests.class)
class RewardOwnershipBoundaryArchTest {

    @ArchTest
    static final ArchRule user_application_must_not_depend_on_wallet_api = noClasses()
            .that().resideInAnyPackage("..user.application..")
            .should().dependOnClassesThat().resideInAnyPackage("..wallet.api..")
            .because("wallet-owned reward projection must not create a user -> wallet synchronous edge");
}
