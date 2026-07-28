package com.nowcoder.community.app.arch;

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
    void coreDomainModelShouldCoverDocumentedMainSiteDomains() {
        assertThat(ArchitectureRulesSupport.CORE_DOMAINS)
                .containsExactlyInAnyOrder(
                        "auth",
                        "user",
                        "content",
                        "social",
                        "notice",
                        "search",
                        "analytics",
                        "growth",
                        "market",
                        "wallet",
                        "drive",
                        "interaction",
                        "profile"
                );
    }

    @Test
    void commonDomainGuardShouldCoverDocumentedBusinessAndAdapterDomains() {
        assertThat(ArchitectureRulesSupport.BUSINESS_OR_ADAPTER_DOMAINS)
                .containsExactlyInAnyOrder(
                        "auth",
                        "user",
                        "content",
                        "social",
                        "notice",
                        "search",
                        "analytics",
                        "growth",
                        "market",
                        "wallet",
                        "drive",
                        "interaction",
                        "ops",
                        "im",
                        "profile"
                );
    }

    @ArchTest
    static final ArchRule core_domains_must_not_depend_on_foreign_implementation_layers =
            classes()
                    .should(ArchitectureRulesSupport.notDependOnForeignCoreLayers(
                            "not depend on foreign controller/mapper/dao/entity/config/security packages",
                            FOREIGN_IMPLEMENTATION_LAYERS
                    ));

    @ArchTest
    static final ArchRule core_domains_must_not_depend_on_ops_or_im =
            classes()
                    .should(ArchitectureRulesSupport.notDependOnDomainsFromCoreOrigins(
                            "not depend on ops or im adapter packages",
                            ArchitectureRulesSupport.ADAPTER_DOMAINS
                    ));

    @ArchTest
    static final ArchRule core_domains_must_only_depend_on_foreign_api_or_contracts =
            classes()
                    .should(ArchitectureRulesSupport.onlyDependOnForeignPackagePrefixes(
                            "only depend on foreign api.query/api.action/api.model/contracts packages",
                            ALLOWED_FOREIGN_COLLABORATION_PACKAGES
                    ));

    @ArchTest
    static final ArchRule common_must_not_depend_on_business_or_adapter_domains =
            classes()
                    .that().resideInAnyPackage("com.nowcoder.community.common..")
                    .should(ArchitectureRulesSupport.notDependOnDomains(
                            "not depend on business or adapter domains",
                            ArchitectureRulesSupport.BUSINESS_OR_ADAPTER_DOMAINS
                    ));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_entities =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("entities", ENTITY_PACKAGE));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_mappers =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("mappers", MAPPER_PACKAGE));

    @ArchTest
    static final ArchRule non_owner_domains_must_not_depend_on_foreign_services =
            classes()
                    .that().resideOutsideOfPackage("..controller..")
                    .should(notDependOnForeignPackage("services", SERVICE_PACKAGE));

    @ArchTest
    static final ArchRule production_code_must_not_use_facade_service_naming =
            classes().should(notUseFacadeServiceNaming());

    @ArchTest
    static final ArchRule production_code_must_not_use_legacy_entry_service_naming =
            classes().should(notUseLegacyEntryServiceNaming());

    @ArchTest
    static final ArchRule owner_api_must_not_depend_on_async_event_contracts =
            noClasses()
                    .that().resideInAnyPackage("..api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..contracts.event..")
                    .because("api.* is the synchronous collaboration boundary and must not leak async event contracts");

    @ArchTest
    static final ArchRule content_api_must_not_depend_on_content_legacy_transport_or_event_payloads =
            noClasses()
                    .that().resideInAnyPackage("..content.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..content.dto..", "..content.contracts.event..")
                    .because("content.api is the synchronous collaboration boundary and must not leak DTOs or async event contracts");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_framework =
            noClasses()
                    .that().resideInAnyPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("domain must not depend on Spring framework");

    @ArchTest
    static final ArchRule ops_domain_must_not_depend_on_framework_or_persistence =
            noClasses()
                    .that().resideInAnyPackage("..ops.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.servlet..",
                            "org.mybatis..",
                            "..mapper..",
                            "..dataobject..",
                            "..controller.dto..",
                            "..infrastructure.."
                    )
                    .because("ops domain expresses governance decisions only");

    private static ArchCondition<JavaClass> notDependOnForeignPackage(
            String packageLabel,
            Pattern trackedPackage
    ) {
        return new ArchCondition<>("not depend on foreign " + packageLabel) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String originDomain = domainOf(item);
                if (originDomain.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (trackedPackage.matcher(target.getPackageName()).matches()
                            && !originDomain.equals(domainOf(target))) {
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
                if (item.getSimpleName().endsWith("FacadeService")) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " ends with FacadeService"
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notUseLegacyEntryServiceNaming() {
        return new ArchCondition<>("not use CommandService or ActionService suffix") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.getSimpleName().endsWith("CommandService")
                        || item.getSimpleName().endsWith("ActionService")) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " uses legacy service entry naming"
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
