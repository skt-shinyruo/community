package com.nowcoder.community.bootstrap.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayerNamingArchTest {

    private static final String ROOT = "com.nowcoder.community";

    @Test
    void controller_classes_should_not_live_in_api_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.getSimpleName().endsWith("Controller")) {
                continue;
            }
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.contains(".api")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Controller classes should live in controller packages, not api packages")
                .isEmpty();
    }

    @Test
    void mapper_interfaces_should_not_live_in_dao_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.getSimpleName().endsWith("Mapper")) {
                continue;
            }
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.contains(".dao")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Mapper interfaces should live in mapper packages, not dao packages")
                .isEmpty();
    }

    @Test
    void dto_classes_should_not_live_in_api_dto_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.contains(".api.dto")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("DTO classes should live in domain dto packages, not api.dto packages")
                .isEmpty();
    }

    @Test
    void security_rule_classes_should_not_live_in_api_security_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.contains(".api.security")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Security rule classes should live in domain security or config packages, not api.security packages")
                .isEmpty();
    }

    @Test
    void event_contract_classes_should_not_live_in_api_event_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.contains(".api.event")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Event contract classes should live in domain event packages, not api.event packages")
                .isEmpty();
    }

    @Test
    void error_code_types_should_not_live_in_api_packages() {
        JavaClasses classes = importMainClasses();
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.getSimpleName().endsWith("ErrorCode")) {
                continue;
            }
            String pkg = javaClass.getPackageName();
            if (pkg != null && pkg.matches(".*\\.api(\\..*)?$")) {
                violations.add(javaClass.getName());
            }
        }
        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Error code types should live in domain exception packages, not api packages")
                .isEmpty();
    }

    private JavaClasses importMainClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }
}
