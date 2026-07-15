package com.nowcoder.community.app.arch;

import com.nowcoder.community.user.domain.service.UserRegistrationDomainService;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community.user",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class UserSpringDaoBoundaryArchTest {

    @ArchTest
    static final ArchRule user_application_and_domain_must_not_depend_on_spring_dao =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.nowcoder.community.user.application..",
                            "com.nowcoder.community.user.domain.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.dao..")
                    .because("persistence must translate Spring DAO failures into User-owned outcomes");

    @Test
    void userDomainMustNotInspectPersistenceExceptionChains() {
        assertThat(UserRegistrationDomainService.class.getDeclaredMethods())
                .filteredOn(method -> Arrays.stream(method.getParameterTypes())
                        .anyMatch(Throwable.class::isAssignableFrom))
                .as("User domain methods must not accept persistence failures for constraint-name parsing")
                .isEmpty();
    }
}
