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
class JsonBoundaryArchTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_jackson_databind =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..")
                    .because("domain code must not depend on JSON tree or mapper infrastructure");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_object_mapper_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.ObjectMapper")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_json_mapper_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.json.JsonMapper")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_serialization_feature_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.SerializationFeature")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule production_code_must_not_depend_on_deserialization_feature_outside_json_infrastructure =
            noClasses()
                    .that().resideOutsideOfPackages(
                            "com.nowcoder.community.common.json..",
                            "com.nowcoder.community.common.web..",
                            "com.nowcoder.community.common.webflux.."
                    )
                    .should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.databind.DeserializationFeature")
                    .because("explicit JSON serialization should use community-common-json");

    @ArchTest
    static final ArchRule common_core_must_not_depend_on_jackson_databind =
            noClasses()
                    .that().resideInAnyPackage("com.nowcoder.community.common.event..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson.databind..")
                    .because("common-core event models must stay JSON-library neutral");
}
