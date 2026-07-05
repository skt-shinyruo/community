package com.nowcoder.community.app.arch;

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

    @ArchTest
    static final ArchRule infrastructure_owner_api_implementations_should_be_named_adapters =
            classes()
                    .that().resideInAnyPackage("..infrastructure.api..")
                    .should(haveApiAdapterNameWhenImplementingOwnerApi());

    private static ArchCondition<JavaClass> haveApiAdapterNameWhenImplementingOwnerApi() {
        return new ArchCondition<>("end with ApiAdapter when implementing published owner APIs") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean implementsOwnerApi = item.getAllRawInterfaces().stream()
                        .map(JavaClass::getPackageName)
                        .anyMatch(packageName -> packageName.contains(".api.query")
                                || packageName.contains(".api.action"));
                if (!implementsOwnerApi || item.getSimpleName().endsWith("ApiAdapter")) {
                    return;
                }
                events.add(SimpleConditionEvent.violated(
                        item,
                        item.getFullName() + " implements api.query/api.action but is not named *ApiAdapter"
                ));
            }
        };
    }
}
