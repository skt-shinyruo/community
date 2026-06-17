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

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(
        packages = "com.nowcoder.community",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ListenerBoundaryArchTest {

    private static final Set<String> LEGACY_LISTENER_APPLICATION_BOUNDARY = Set.of();
    private static final Set<String> LEGACY_INBOUND_FOREIGN_API_BOUNDARY = Set.of();

    @Test
    void listenerApplicationBoundaryShouldNotRequireLegacyExceptions() {
        assertThat(LEGACY_LISTENER_APPLICATION_BOUNDARY).isEmpty();
    }

    @Test
    void inboundForeignApiBoundaryShouldNotRequireLegacyExceptions() {
        assertThat(LEGACY_INBOUND_FOREIGN_API_BOUNDARY).isEmpty();
    }

    @ArchTest
    static final ArchRule listeners_must_not_depend_on_same_domain_non_application_entry_points =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnSameDomainServicesExceptApplicationServices(
                            LEGACY_LISTENER_APPLICATION_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_enter_same_domain_application_service_before_helpers =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnSameDomainApplicationHelpersBeforeApplicationService(
                            LEGACY_LISTENER_APPLICATION_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_not_bypass_application_service_to_same_domain_domain_or_persistence =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnSameDomainDomainOrPersistenceBeforeApplicationService(
                            LEGACY_LISTENER_APPLICATION_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_foreign_owner_apis =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnForeignOwnerApiPackages(
                            LEGACY_INBOUND_FOREIGN_API_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_not_depend_on_foreign_application_packages =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(ArchitectureRulesSupport.notDependOnForeignApplicationPackages(
                            LEGACY_INBOUND_FOREIGN_API_BOUNDARY
                    ));

    @ArchTest
    static final ArchRule inbound_adapters_must_not_send_kafka_directly =
            classes()
                    .that().resideInAnyPackage(
                            "..infrastructure.event..",
                            "..infrastructure.job..",
                            "..infra.job.handlers.."
                    )
                    .and().haveNameMatching(".*(Listener|Handler|Bridge|Enqueuer|Job)$")
                    .should(notDependOnKafkaSendingInfrastructure());

    private static ArchCondition<JavaClass> notDependOnKafkaSendingInfrastructure() {
        return new ArchCondition<>("not depend on KafkaTemplate or TraceKafkaSender before an application dispatch boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getFullName();
                    if ("org.springframework.kafka.core.KafkaTemplate".equals(target)
                            || "com.nowcoder.community.common.kafka.trace.TraceKafkaSender".equals(target)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }
}
