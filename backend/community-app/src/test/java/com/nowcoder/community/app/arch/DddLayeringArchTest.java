package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class DddLayeringArchTest {

    @Test
    void coreDomainInventoryShouldCoverDrive() {
        assertThat(ArchitectureRulesSupport.CORE_DOMAINS).contains("drive");
    }

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..application..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto..",
                            "..api.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring_framework =
            noClasses()
                    .that().resideInAnyPackage(
                            "..domain.model..",
                            "..domain.service..",
                            "..domain.repository..",
                            "..domain.event.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("domain code must remain plain Java and must not depend on Spring");

    @ArchTest
    static final ArchRule application_must_not_depend_on_transport_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..",
                            "..app..",
                            "..content.service..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity..",
                            "..dto.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_must_not_depend_on_web_transport_types =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.http..",
                            "org.springframework.core.io..",
                            "org.springframework.web..",
                            "org.springframework.web.multipart..",
                            "jakarta.servlet.."
                    )
                    .because("HTTP transport details belong in controllers or web adapters");

    @ArchTest
    static final ArchRule application_services_must_not_return_web_transport_types =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .and().haveSimpleNameEndingWith("ApplicationService")
                    .should(notReturnWebTransportTypes());

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_own_transactions =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().beAnnotatedWith(Transactional.class)
                    .because("content write transaction boundaries belong in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_classes_must_not_own_transactions =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().beAnnotatedWith(Transactional.class)
                    .because("content write transaction boundaries belong in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_call_foreign_owner_apis =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..", "..api.model..")
                    .because("foreign synchronous collaboration belongs in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auth_infrastructure_must_not_call_foreign_owner_apis =
            noClasses()
                    .that().resideInAnyPackage("..auth.infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..", "..api.model..")
                    .because("foreign synchronous collaboration belongs in auth application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_depend_on_content_event_adapters =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..content.infrastructure.event..",
                            "..content.contracts.event.."
                    )
                    .because("business event publication belongs in application or event adapters")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_use_spring_event_publisher =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().haveSimpleName("ApplicationEventPublisher")
                    .because("business event publication belongs in application or event adapters")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_application_port_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..content.application.port..")
                    .because("content persistence contracts belong in domain.repository and technical ports belong in application root")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_application_assembler_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..content.application.assembler..")
                    .because("content application assemblers live in the application root or controller boundary")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule content_infrastructure_persistence_services_must_stay_retired =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().haveSimpleNameEndingWith("Service")
                    .because("content persistence implementations use MyBatis*Repository or explicit adapter names")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule production_code_must_not_use_use_case_naming =
            noClasses()
                    .should().haveSimpleNameEndingWith("UseCase")
                    .because("ApplicationService is the use-case entry; do not add a parallel UseCase layer");

    @ArchTest
    static final ArchRule domain_named_application_services_must_not_be_facade_entries =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .and().haveSimpleNameEndingWith("ApplicationService")
                    .should(notBeDomainNamedApplicationFacade())
                    .because("domain-named ApplicationService classes obscure which concrete use case owns transactions, idempotency, audit, and cross-domain collaboration");

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_domain_or_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("..controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..domain..",
                            "..infrastructure..",
                            "..mapper..",
                            "..entity.."
                    );

    @ArchTest
    static final ArchRule legacy_business_root_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage(
                            "..analytics.service..",
                            "..analytics.repo..",
                            "..analytics.ingest..",
                            "..auth.dto..",
                            "..auth.service..",
                            "..auth.web..",
                            "..content.app..",
                            "..content.assembler..",
                            "..content.dto..",
                            "..content.entity..",
                            "..content.event..",
                            "..content.like..",
                            "..content.mapper..",
                            "..content.score..",
                            "..content.service..",
                            "..content.text..",
                            "..content.util..",
                            "..content.domain.assembler..",
                            "..growth.entity..",
                            "..growth.event..",
                            "..growth.mapper..",
                            "..growth.service..",
                            "..im.service..",
                            "..market.entity..",
                            "..market.job..",
                            "..market.mapper..",
                            "..market.model..",
                            "..market.service..",
                            "..notice.entity..",
                            "..notice.event..",
                            "..notice.mapper..",
                            "..notice.service..",
                            "..ops.dto..",
                            "..search.event..",
                            "..search.repo..",
                            "..search.service..",
                            "..social.block..",
                            "..social.dto..",
                            "..social.event..",
                            "..social.follow..",
                            "..social.like..",
                            "..social.service..",
                            "..user.dto..",
                            "..user.entity..",
                            "..user.event..",
                            "..user.mapper..",
                            "..user.service..",
                            "..user.session..",
                            "..wallet.entity..",
                            "..wallet.mapper..",
                            "..wallet.model..",
                            "..wallet.service.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule user_controllers_must_not_depend_on_legacy_user_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..user.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..user.service..",
                            "..user.mapper..",
                            "..user.entity..",
                            "..user.infrastructure.."
                    );

    @ArchTest
    static final ArchRule auth_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..auth.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_auth_web_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..auth.web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auth_controller_and_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage(
                            "..auth.controller..",
                            "..auth.infrastructure.job..",
                            "..auth.infrastructure.web.."
                    )
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..auth.service..",
                            "..auth.web..",
                            "..auth.domain..",
                            "..auth.infrastructure.persistence..",
                            "..auth.infrastructure.jwt..",
                            "..auth.infrastructure.mail.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule ops_controller_must_call_ops_application_only =
            noClasses()
                    .that().resideInAnyPackage("..ops.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..search.api..",
                            "..ops.infrastructure..",
                            "..ops.dto.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_ops_dto_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..ops.dto..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule user_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..user.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_user_mapper_and_entity_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..user.mapper..", "..user.entity..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..social.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_social_feature_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage(
                            "..social.like..",
                            "..social.follow..",
                            "..social.block..",
                            "..social.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule social_controllers_must_not_depend_on_legacy_social_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..social.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..social.service..",
                            "..social.infrastructure..",
                            "..social.domain..",
                            "..social.like..",
                            "..social.follow..",
                            "..social.block..",
                            "..social.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule wallet_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..wallet.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_wallet_mapper_entity_model_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..wallet.mapper..", "..wallet.entity..", "..wallet.model..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule wallet_controllers_must_not_depend_on_legacy_wallet_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..wallet.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..wallet.service..",
                            "..wallet.infrastructure..",
                            "..wallet.domain..",
                            "..wallet.mapper..",
                            "..wallet.entity..",
                            "..wallet.model.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule market_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..market.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_market_mapper_entity_model_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..market.mapper..", "..market.entity..", "..market.model..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule market_domain_repositories_must_use_domain_method_names =
            classes()
                    .that().resideInAnyPackage("..market.domain.repository..")
                    .should(notDeclareMethodsStartingWith("select", "insert", "update"));

    @ArchTest
    static final ArchRule market_applications_must_not_name_repositories_as_mappers =
            classes()
                    .that().resideInAnyPackage("..market.application..")
                    .should(notDeclareFieldsEndingWith("Mapper"));

    @ArchTest
    static final ArchRule market_controllers_and_jobs_must_not_depend_on_legacy_market_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..market.controller..", "..market.infrastructure.job..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..market.service..",
                            "..market.infrastructure..",
                            "..market.domain..",
                            "..market.mapper..",
                            "..market.entity..",
                            "..market.model.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule growth_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..growth.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_growth_mapper_entity_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..growth.mapper..", "..growth.entity..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..notice.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_notice_mapper_entity_event_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..notice.mapper..", "..notice.entity..", "..notice.event..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notice_controllers_and_listeners_must_not_depend_on_legacy_notice_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..notice.controller..", "..notice.infrastructure.event..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..notice.service..",
                            "..notice.mapper..",
                            "..notice.entity..",
                            "..notice.event.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule search_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..search.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_search_repo_event_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..search.repo..", "..search.event..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule search_controller_and_event_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..search.controller..", "..search.infrastructure.event..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..search.service..",
                            "..search.repo..",
                            "..search.event..",
                            "..search.infrastructure.persistence..",
                            "..search.domain.."
                    )
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule analytics_service_package_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..analytics.service..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule legacy_analytics_repo_ingest_packages_must_stay_retired =
            noClasses()
                    .should().resideInAnyPackage("..analytics.repo..", "..analytics.ingest..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule analytics_controller_and_web_adapters_must_not_depend_on_legacy_surfaces =
            noClasses()
                    .that().resideInAnyPackage("..analytics.controller..", "..analytics.infrastructure.web..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..analytics.service..",
                            "..analytics.repo..",
                            "..analytics.ingest..",
                            "..analytics.infrastructure.persistence..",
                            "..analytics.domain.."
                    )
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notReturnWebTransportTypes() {
        Set<String> forbiddenTypeNames = Set.of(
                "org.springframework.http.ResponseCookie",
                "org.springframework.http.ResponseEntity",
                "org.springframework.http.MediaType",
                "org.springframework.core.io.Resource",
                "jakarta.servlet.http.HttpServletRequest",
                "jakarta.servlet.http.HttpServletResponse"
        );
        return new ArchCondition<>("not return HTTP transport types from public application service methods") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    JavaClass returnType = method.getRawReturnType();
                    if (forbiddenTypeNames.contains(returnType.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                method.getFullName() + " returns " + returnType.getName()
                        ));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notBeDomainNamedApplicationFacade() {
        return new ArchCondition<>("not be a domain-named facade over same-domain application services") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String domain = ArchitectureRulesSupport.domainOf(item);
                if (domain.isBlank()) {
                    return;
                }
                String simpleName = item.getSimpleName();
                String domainName = toUpperCamel(domain);
                boolean domainEntryName = simpleName.equals(domainName + "ApplicationService")
                        || simpleName.equals("Admin" + domainName + "ApplicationService");
                if (!domainEntryName) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!domain.equals(ArchitectureRulesSupport.domainOf(target))) {
                        continue;
                    }
                    if (!target.getPackageName().endsWith(".application")) {
                        continue;
                    }
                    if (!target.getSimpleName().endsWith("ApplicationService")) {
                        continue;
                    }
                    if (target.getFullName().equals(item.getFullName())) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            dependency,
                            dependency.getDescription()
                    ));
                }
            }
        };
    }

    private static String toUpperCamel(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static ArchCondition<JavaClass> notDeclareMethodsStartingWith(String... forbiddenPrefixes) {
        return new ArchCondition<>("not declare methods starting with mapper-style prefixes") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    for (String prefix : forbiddenPrefixes) {
                        if (method.getName().startsWith(prefix)) {
                            events.add(SimpleConditionEvent.violated(
                                    item,
                                    method.getFullName() + " starts with " + prefix
                            ));
                        }
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notDeclareFieldsEndingWith(String forbiddenSuffix) {
        return new ArchCondition<>("not declare fields ending with " + forbiddenSuffix) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getFields().forEach(field -> {
                    if (field.getName().endsWith(forbiddenSuffix)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                field.getFullName() + " ends with " + forbiddenSuffix
                        ));
                    }
                });
            }
        };
    }
}
