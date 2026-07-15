package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = {
                "com.nowcoder.community.wallet",
                "com.nowcoder.community.market"
        },
        importOptions = ImportOption.DoNotIncludeTests.class
)
class PersistenceExceptionBoundaryArchTest {

    @ArchTest
    static final ArchRule wallet_and_market_core_must_not_depend_on_spring_dao =
            noClasses()
                    .that().resideInAnyPackage(
                            "..wallet.application..",
                            "..wallet.domain..",
                            "..market.application..",
                            "..market.domain.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.dao..")
                    .because("duplicate-key translation belongs in MyBatis repository adapters");
}
