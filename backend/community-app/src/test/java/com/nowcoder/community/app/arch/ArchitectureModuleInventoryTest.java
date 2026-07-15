package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureModuleInventoryTest {

    @Test
    void tacticalRootDiscoveryMustUseExactPackageSegments() {
        assertThat(ArchitectureRulesSupport.discoverTacticalRoots(Set.of(
                "com.nowcoder.community.content.application",
                "com.nowcoder.community.content.application.command",
                "com.nowcoder.community.social.domain.model",
                "com.nowcoder.community.runtime.controller",
                "com.nowcoder.community.infra.persistence",
                "com.nowcoder.community.fake.myapplication",
                "org.example.foreign.application"
        ))).containsExactlyInAnyOrder("content", "social", "runtime");
    }

    @Test
    void reviewedModuleInventoryMustExactlyMatchProductionTacticalRoots() throws URISyntaxException {
        Set<JavaClass> productionClasses = new TreeSet<>((left, right) -> left.getName().compareTo(right.getName()));
        new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPath(productionClassesPath())
                .forEach(productionClasses::add);

        Set<String> productionPackages = new TreeSet<>();
        Set<String> productionTopLevelRoots = new TreeSet<>();
        for (JavaClass javaClass : productionClasses) {
            productionPackages.add(javaClass.getPackageName());
            productionTopLevelRoots.add(ArchitectureRulesSupport.domainOf(javaClass));
        }

        assertThat(ArchitectureRulesSupport.discoverTacticalRoots(productionPackages))
                .containsExactlyInAnyOrderElementsOf(ArchitectureRulesSupport.TACTICAL_ROOTS);
        assertThat(productionTopLevelRoots)
                .containsExactlyInAnyOrderElementsOf(ArchitectureRulesSupport.CLASSIFIED_TOP_LEVEL_ROOTS);
    }

    @Test
    void moduleClassificationsMustNotOverlap() {
        assertThat(ArchitectureRulesSupport.CORE_DOMAINS)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.ADAPTER_DOMAINS)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.PLATFORM_MODULES)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.TECHNICAL_ROOTS);
        assertThat(ArchitectureRulesSupport.ADAPTER_DOMAINS)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.PLATFORM_MODULES)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.TECHNICAL_ROOTS);
        assertThat(ArchitectureRulesSupport.PLATFORM_MODULES)
                .doesNotContainAnyElementsOf(ArchitectureRulesSupport.TECHNICAL_ROOTS);
    }

    private static Path productionClassesPath() throws URISyntaxException {
        Path testClasses = Path.of(ArchitectureModuleInventoryTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        return testClasses.getParent().resolve("classes");
    }
}
