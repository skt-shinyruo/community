package com.nowcoder.community.app.arch;

import com.nowcoder.community.social.domain.repository.BlockRepository;
import com.nowcoder.community.social.domain.repository.FollowRepository;
import com.nowcoder.community.social.domain.repository.LikeRepository;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community.social",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class SocialRepositoryBoundaryArchTest {

    @ArchTest
    static final ArchRule social_application_must_not_use_spring_transaction_synchronization =
            noClasses()
                    .that().resideInAnyPackage("..social.application..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction.support..")
                    .because("application transaction boundaries must not branch on repository storage technology");

    @ArchTest
    static final ArchRule social_persistence_must_not_own_transactions =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage("..social.infrastructure.persistence..")
                    .should().beAnnotatedWith(Transactional.class)
                    .because("social write transactions belong to Social ApplicationService entry points");

    @ArchTest
    static final ArchRule social_persistence_must_not_be_selected_as_alternative_write_stores =
            noClasses()
                    .that().resideInAnyPackage("..social.infrastructure.persistence..")
                    .should().beAnnotatedWith(ConditionalOnProperty.class)
                    .because("the Social correctness write model has MySQL as its only source of truth");

    @Test
    void socialRepositoriesMustNotExposeCompensationCapabilities() {
        List<String> technicalMethods = List.of(LikeRepository.class, FollowRepository.class, BlockRepository.class).stream()
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream())
                .map(Method::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).contains("compensation"))
                .sorted()
                .toList();

        assertThat(technicalMethods)
                .as("domain repositories must not reveal whether their storage participates in Spring transactions")
                .isEmpty();
    }

    @Test
    void redisMustNotImplementSocialWriteRepositories() {
        List<String> redisRepositories = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community.social.infrastructure.persistence")
                .stream()
                .filter(javaClass -> javaClass.getSimpleName().startsWith("Redis"))
                .filter(this::implementsSocialWriteRepository)
                .map(JavaClass::getName)
                .sorted()
                .toList();

        assertThat(redisRepositories)
                .as("Redis may be introduced only as a cache/projection port, not as a Social write repository")
                .isEmpty();
    }

    private boolean implementsSocialWriteRepository(JavaClass javaClass) {
        return javaClass.getAllRawInterfaces().stream()
                .map(JavaClass::getName)
                .anyMatch(name -> name.equals(LikeRepository.class.getName())
                        || name.equals(FollowRepository.class.getName())
                        || name.equals(BlockRepository.class.getName()));
    }
}
