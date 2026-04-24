package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ListenerBoundaryArchTest {

    private static final Set<String> LEGACY_LISTENER_APPLICATION_BOUNDARY = Set.of();

    @Test
    void listenerApplicationBoundaryShouldNotRequireLegacyExceptions() {
        assertThat(LEGACY_LISTENER_APPLICATION_BOUNDARY).isEmpty();
    }

    @ArchTest
    static final ArchRule listeners_must_not_depend_on_same_domain_non_application_entry_points =
            classes()
                    .that().resideInAnyPackage("..event..")
                    .and().haveSimpleNameEndingWith("Listener")
                    .should(ArchitectureRulesSupport.notDependOnSameDomainServicesExceptApplicationServices(
                            LEGACY_LISTENER_APPLICATION_BOUNDARY
                    ));
}
