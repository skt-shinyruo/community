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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
                            "..dto..",
                            "com.nowcoder.community.infra.."
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
    static final ArchRule application_must_not_depend_on_broker_transport_types =
            noClasses()
                    .that().resideInAnyPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.kafka..",
                            "org.apache.kafka..",
                            "..common.kafka.."
                    )
                    .because("broker transport details belong in infrastructure adapters");

    @ArchTest
    static final ArchRule application_ports_must_not_expose_transport_vocabulary =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .and().areInterfaces()
                    .should(notExposeTransportVocabularyInApplicationPort());

    @ArchTest
    static final ArchRule content_infrastructure_persistence_must_not_call_foreign_owner_apis =
            noClasses()
                    .that().resideInAnyPackage("..content.infrastructure.persistence..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..", "..api.model..")
                    .because("foreign synchronous collaboration belongs in application services")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auth_non_api_infrastructure_must_not_call_foreign_owner_apis =
            noClasses()
                    .that().resideInAnyPackage("..auth.infrastructure..")
                    .and().resideOutsideOfPackage("..auth.infrastructure.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..", "..api.model..")
                    .because("foreign synchronous collaboration from auth infrastructure is limited to outbound API adapters")
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
    static final ArchRule owner_packages_must_use_the_owner_first_structure =
            classes()
                    .that().resideInAnyPackage(ownerPackagePatterns())
                    .and().resideOutsideOfPackage("com.nowcoder.community.im.common..")
                    .should(resideInApprovedOwnerRootPackage())
                    .because("community-app owner code uses explicit controller/application/domain/infrastructure/API roots; "
                            + "the separately built im-common module publishes shared wire contracts");

    @ArchTest
    static final ArchRule application_services_must_have_one_explicit_constructor =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .and().haveSimpleNameEndingWith("ApplicationService")
                    .should(haveExactlyOneConstructor());

    @ArchTest
    static final ArchRule application_must_receive_clock_and_uuid_v7_generator_dependencies =
            classes()
                    .that().resideInAnyPackage("..application..")
                    .should(notCreateClockOrUuidV7Generator())
                    .because("time and ordered ID policy belongs in the Spring composition root");

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

    private static String[] ownerPackagePatterns() {
        return ArchitectureRulesSupport.TACTICAL_ROOTS.stream()
                .map(owner -> "com.nowcoder.community." + owner + "..")
                .toArray(String[]::new);
    }

    private static ArchCondition<JavaClass> resideInApprovedOwnerRootPackage() {
        Set<String> approvedRoots = Set.of(
                "api",
                "application",
                "config",
                "contracts",
                "controller",
                "domain",
                "exception",
                "infrastructure",
                "logging",
                "security"
        );
        return new ArchCondition<>("reside in an approved owner-first root package") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String owner = ArchitectureRulesSupport.domainOf(item);
                if (!ArchitectureRulesSupport.TACTICAL_ROOTS.contains(owner)) {
                    return;
                }
                String prefix = "com.nowcoder.community." + owner + ".";
                String relativePackage = item.getPackageName().substring(prefix.length());
                String root = relativePackage.contains(".")
                        ? relativePackage.substring(0, relativePackage.indexOf('.'))
                        : relativePackage;
                if (!approvedRoots.contains(root)) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " resides in unapproved owner root '" + root + "'"
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveExactlyOneConstructor() {
        return new ArchCondition<>("declare exactly one non-empty constructor") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.getConstructors().size() != 1) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            item.getName() + " declares " + item.getConstructors().size() + " constructors"
                    ));
                    return;
                }
                var constructor = item.getConstructors().iterator().next();
                if (constructor.getRawParameterTypes().isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            constructor.getFullName() + " has no explicit dependencies"
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notCreateClockOrUuidV7Generator() {
        return new ArchCondition<>("not create Clock or UuidV7Generator dependencies") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getConstructorCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().getName()
                                .equals("com.nowcoder.community.common.id.UuidV7Generator"))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(
                                call,
                                call.getDescription()
                        )));
                item.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().getName().equals("java.time.Clock"))
                        .filter(call -> call.getName().startsWith("system"))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(
                                call,
                                call.getDescription()
                        )));
            }
        };
    }

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

    private static ArchCondition<JavaClass> notExposeTransportVocabularyInApplicationPort() {
        Set<String> forbiddenNameParts = Set.of("Kafka", "Rabbit", "Redis", "MyBatis", "Jdbc", "Http");
        Set<String> forbiddenParameterNames = Set.of(
                "org.springframework.kafka.core.KafkaTemplate",
                "org.apache.kafka.clients.consumer.ConsumerRecord",
                "org.apache.kafka.clients.producer.ProducerRecord"
        );
        Set<String> forbiddenSignatureVocabulary = Set.of("topic", "partition", "offset", "key");
        return new ArchCondition<>("not expose broker or infrastructure vocabulary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (String forbiddenNamePart : forbiddenNameParts) {
                    if (item.getSimpleName().contains(forbiddenNamePart)) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                item.getFullName() + " contains transport/infrastructure vocabulary: " + forbiddenNamePart
                        ));
                    }
                }
                for (JavaMethod method : item.getMethods()) {
                    String lowerMethodName = method.getName().toLowerCase();
                    for (String forbiddenSignaturePart : forbiddenSignatureVocabulary) {
                        if (lowerMethodName.contains(forbiddenSignaturePart)) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName() + " exposes broker routing vocabulary: " + forbiddenSignaturePart
                            ));
                        }
                    }
                    if ((lowerMethodName.equals("send") || lowerMethodName.equals("publish"))
                            && !method.getRawParameterTypes().isEmpty()
                            && "java.lang.String".equals(method.getRawParameterTypes().get(0).getName())) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " uses broker-shaped String-first send/publish signature"
                        ));
                    }
                    JavaClass returnType = method.getRawReturnType();
                    if (forbiddenParameterNames.contains(returnType.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " returns " + returnType.getName()
                        ));
                    }
                    for (String forbiddenNamePart : forbiddenNameParts) {
                        if (returnType.getSimpleName().contains(forbiddenNamePart)) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName() + " returns transport/infrastructure vocabulary: " + forbiddenNamePart
                            ));
                        }
                    }
                    for (JavaClass parameterType : method.getRawParameterTypes()) {
                        if (forbiddenParameterNames.contains(parameterType.getName())) {
                            events.add(SimpleConditionEvent.violated(
                                    method,
                                    method.getFullName() + " exposes " + parameterType.getName()
                            ));
                        }
                        for (String forbiddenNamePart : forbiddenNameParts) {
                            if (parameterType.getSimpleName().contains(forbiddenNamePart)) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName() + " exposes transport/infrastructure vocabulary: " + forbiddenNamePart
                                ));
                            }
                        }
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
