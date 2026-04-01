package com.nowcoder.community.app.arch;

import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainBoundaryArchTest {

    private static final String BASE_PACKAGE = "com.nowcoder.community.";
    private static final Pattern ENTITY_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.entity(\\..*)?");
    private static final Pattern MAPPER_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.mapper(\\..*)?");
    private static final Pattern SERVICE_PACKAGE =
            Pattern.compile("com\\.nowcoder\\.community\\.[^.]+\\.service(\\..*)?");

    private static final Set<String> BUSINESS_OR_ADAPTER_DOMAINS = Set.of(
            "auth",
            "user",
            "content",
            "social",
            "message",
            "notice",
            "search",
            "analytics",
            "growth",
            "ops",
            "im"
    );
    private static final Set<String> LEGACY_FOREIGN_ENTITY_CALLERS = Set.of();
    private static final Set<String> LEGACY_FOREIGN_MAPPER_CALLERS = Set.of();
    private static final Set<String> LEGACY_FOREIGN_SERVICE_CALLERS = Set.of();
    private static final Set<String> LEGACY_FACADE_SERVICE_CLASSES = Set.of();
    private static final Set<String> LEGACY_FOREIGN_NON_COLLABORATION_CALLERS = Set.of();
    private static final Set<String> FOREIGN_IMPLEMENTATION_LAYERS = Set.of(
            "controller",
            "mapper",
            "dao",
            "entity",
            "config",
            "security"
    );
    private static final Set<String> ALLOWED_FOREIGN_COLLABORATION_PACKAGES = Set.of(
            "api.query",
            "api.action",
            "api.model",
            "contracts"
    );

    @Test
    void coreDomainModelShouldTreatNoticeAsFirstClassDomain() {
        assertThat(ArchitectureRulesSupport.CORE_DOMAINS).contains("notice");
    }

    @Test
    void commonDomainGuardShouldIncludeNoticeDomain() {
        assertThat(BUSINESS_OR_ADAPTER_DOMAINS).contains("notice");
    }

    @Test
    void socialWriteServicesShouldNotReadStoragePropertyDirectly() {
        assertThat(hasValueInjection(LikeService.class, "social.storage")).isFalse();
        assertThat(hasValueInjection(FollowService.class, "social.storage")).isFalse();
    }

    @Test
    void entityBoundaryShouldOnlyPermitApprovedSharedMessageReuse() {
        assertThat(LEGACY_FOREIGN_ENTITY_CALLERS).isEmpty();
        assertThat(ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN)
                .containsOnlyKeys(
                        "com.nowcoder.community.notice.controller.NoticeController",
                        "com.nowcoder.community.notice.mapper.NoticeMapper",
                        "com.nowcoder.community.notice.service.NoticeItemAssembler",
                        "com.nowcoder.community.notice.service.NoticeService"
                )
                .containsEntry(
                        "com.nowcoder.community.notice.mapper.NoticeMapper",
                        Set.of("com.nowcoder.community.message.entity.Message")
                )
                .containsEntry(
                        "com.nowcoder.community.notice.service.NoticeItemAssembler",
                        Set.of(
                                "com.nowcoder.community.message.dto.LetterItemResponse",
                                "com.nowcoder.community.message.entity.Message"
                        )
                )
                .containsEntry(
                        "com.nowcoder.community.notice.service.NoticeService",
                        Set.of(
                                "com.nowcoder.community.message.dto.LetterItemResponse",
                                "com.nowcoder.community.message.dto.NoticeTopicSummaryResponse",
                                "com.nowcoder.community.message.entity.Message"
                        )
                );
    }

    @ArchTest
    static final ArchRule core_domains_must_not_depend_on_foreign_implementation_layers =
            classes()
                    .should(ArchitectureRulesSupport.notDependOnForeignCoreLayers(
                            "not depend on foreign controller/mapper/dao/entity/config/security packages",
                            FOREIGN_IMPLEMENTATION_LAYERS,
                            ArchitectureRulesSupport.MIGRATION_BASELINE_FOREIGN_IMPLEMENTATION_CALLERS,
                            ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN
                    ));

    @ArchTest
    static final ArchRule core_domains_must_not_depend_on_ops_or_im =
            classes()
                    .should(ArchitectureRulesSupport.notDependOnDomainsFromCoreOrigins(
                            "not depend on ops or im adapter packages",
                            Set.of("ops", "im"),
                            Set.of()
                    ));

    @ArchTest
    static final ArchRule core_domains_must_only_depend_on_foreign_api_or_contracts =
            classes()
                    .should(ArchitectureRulesSupport.onlyDependOnForeignPackagePrefixes(
                            "only depend on foreign api.query/api.action/api.model/contracts packages",
                            ALLOWED_FOREIGN_COLLABORATION_PACKAGES,
                            LEGACY_FOREIGN_NON_COLLABORATION_CALLERS,
                            ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN
                    ));

    @ArchTest
    static final ArchRule common_must_not_depend_on_business_or_adapter_domains =
            classes()
                    .that().resideInAnyPackage("..common..")
                    .should(ArchitectureRulesSupport.notDependOnDomains(
                            "not depend on business or adapter domains",
                            BUSINESS_OR_ADAPTER_DOMAINS,
                            Set.of()
                    ));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_entities =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage(
                            "entities",
                            ENTITY_PACKAGE,
                            LEGACY_FOREIGN_ENTITY_CALLERS,
                            ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN
                    ));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_mappers =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("mappers", MAPPER_PACKAGE, LEGACY_FOREIGN_MAPPER_CALLERS));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_services =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("services", SERVICE_PACKAGE, LEGACY_FOREIGN_SERVICE_CALLERS));

    @ArchTest
    static final ArchRule production_code_must_not_use_facade_service_naming =
            classes().should(notUseFacadeServiceNaming());

    @ArchTest
    static final ArchRule content_api_must_not_depend_on_content_legacy_transport_or_event_payloads =
            noClasses()
                    .that().resideInAnyPackage("..content.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..content.dto..", "..content.contracts.event..")
                    .because("content.api is the synchronous collaboration boundary and must not leak DTOs or async event contracts");

    private static ArchCondition<JavaClass> notDependOnForeignPackage(
            String packageLabel,
            Pattern trackedPackage,
            Set<String> legacyCallers
    ) {
        return notDependOnForeignPackage(packageLabel, trackedPackage, legacyCallers, Map.of());
    }

    private static ArchCondition<JavaClass> notDependOnForeignPackage(
            String packageLabel,
            Pattern trackedPackage,
            Set<String> legacyCallers,
            Map<String, Set<String>> allowedTargetTypesByOrigin
    ) {
        return new ArchCondition<>("not depend on foreign " + packageLabel) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (legacyCallers.contains(item.getName())) {
                    return;
                }
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (trackedPackage.matcher(target.getPackageName()).matches()
                            && !originDomain.equals(domainOf(target))
                            && !ArchitectureRulesSupport.isAllowedTargetDependency(item, target, allowedTargetTypesByOrigin)) {
                        events.add(SimpleConditionEvent.violated(item, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notUseFacadeServiceNaming() {
        return new ArchCondition<>("not use FacadeService suffix") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.getSimpleName().endsWith("FacadeService")
                        && !LEGACY_FACADE_SERVICE_CLASSES.contains(item.getName())) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " ends with FacadeService"
                    ));
                }
            }
        };
    }

    private static String domainOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE)) {
            return "";
        }
        int domainStart = BASE_PACKAGE.length();
        int domainEnd = packageName.indexOf('.', domainStart);
        if (domainEnd < 0) {
            return packageName.substring(domainStart);
        }
        return packageName.substring(domainStart, domainEnd);
    }

    private static boolean hasValueInjection(Class<?> type, String propertyFragment) {
        for (Field field : type.getDeclaredFields()) {
            if (containsValueProperty(field.getAnnotation(Value.class), propertyFragment)) {
                return true;
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (hasValueParameter(constructor.getParameters(), propertyFragment)) {
                return true;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (containsValueProperty(method.getAnnotation(Value.class), propertyFragment)
                    || hasValueParameter(method.getParameters(), propertyFragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValueParameter(Parameter[] parameters, String propertyFragment) {
        for (Parameter parameter : parameters) {
            if (containsValueProperty(parameter.getAnnotation(Value.class), propertyFragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsValueProperty(Value value, String propertyFragment) {
        return value != null && value.value() != null && value.value().contains(propertyFragment);
    }
}
