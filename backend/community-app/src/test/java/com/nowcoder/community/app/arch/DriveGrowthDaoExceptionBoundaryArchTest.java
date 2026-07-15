package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class DriveGrowthDaoExceptionBoundaryArchTest {

    @Test
    void driveAndGrowthApplicationMustNotDependOnSpringDao() {
        Set<String> violations = new TreeSet<>();
        new ClassFileImporter()
                .importPackages(
                        "com.nowcoder.community.drive.application",
                        "com.nowcoder.community.growth.application"
                )
                .stream()
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith("org.springframework.dao"))
                .map(Dependency::getDescription)
                .forEach(violations::add);

        assertThat(violations)
                .as("persistence adapters must translate DAO failures into repository outcomes")
                .isEmpty();
    }
}
