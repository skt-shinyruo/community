package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class InfraBoundaryArchTest {

    private static final Set<String> FOREIGN_IMPLEMENTATION_LAYERS = Set.of(
            "controller",
            "mapper",
            "dao",
            "entity",
            "config",
            "security",
            "service"
    );

    @ArchTest
    static final ArchRule infra_must_not_depend_on_core_domain_implementation_layers =
            classes()
                    .that().resideInAnyPackage("..infra..", "..infrastructure..")
                    .should(ArchitectureRulesSupport.notDependOnLayers(
                            "not depend on core-domain controller/mapper/dao/entity/config/security/service packages",
                            FOREIGN_IMPLEMENTATION_LAYERS,
                            true,
                            Set.of()
                    ));
}
