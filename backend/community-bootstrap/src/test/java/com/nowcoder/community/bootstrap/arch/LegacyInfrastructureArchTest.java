package com.nowcoder.community.bootstrap.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyInfrastructureArchTest {

    private static final String ROOT = "com.nowcoder.community";
    private static final String LEGACY_MODULECALL_PREFIX = ROOT + ".infra.modulecall";

    @Test
    void production_code_should_not_depend_on_legacy_modulecall_support() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        List<String> violations = new ArrayList<>();
        for (JavaClass origin : classes) {
            for (Dependency dep : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                if (target == null) {
                    continue;
                }
                String targetPkg = target.getPackageName();
                if (targetPkg != null && (targetPkg.equals(LEGACY_MODULECALL_PREFIX)
                        || targetPkg.startsWith(LEGACY_MODULECALL_PREFIX + "."))) {
                    violations.add("[" + origin.getName() + "] -> [" + target.getName() + "] (" + dep.getDescription() + ")");
                }
            }
        }

        violations.sort(Comparator.naturalOrder());
        assertThat(violations)
                .as("Production code should not depend on legacy infra.modulecall support")
                .isEmpty();
    }
}
