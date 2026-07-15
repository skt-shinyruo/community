package com.nowcoder.community.app.arch;

import com.fasterxml.jackson.databind.JsonNode;
import com.nowcoder.community.market.domain.model.MarketOrder;
import com.nowcoder.community.wallet.domain.model.WalletAccount;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SharedArchitectureInvariantArchTest {

    private static final Set<String> EVENT_ENVELOPES = Set.of(
            "com.nowcoder.community.content.contracts.event.ContentContractEvent",
            "com.nowcoder.community.social.contracts.event.SocialContractEvent",
            "com.nowcoder.community.user.contracts.event.UserContractEvent"
    );

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.nowcoder.community");

    @Test
    void applicationAndDomainMustNotDependOnSpringDaoOrTransactionSupport() {
        List<String> violations = productionClasses.stream()
                .filter(this::isApplicationOrDomain)
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isSpringDaoOrTransactionImplementation(dependency.getTargetClass()))
                .map(Dependency::getDescription)
                .sorted()
                .toList();

        assertThat(violations)
                .as("application/domain must use semantic outcomes and application-owned ports")
                .isEmpty();
    }

    @Test
    void applicationAndDomainMustNotDependOnForeignEventContracts() {
        List<String> violations = new ArrayList<>();
        for (JavaClass origin : productionClasses) {
            if (!isApplicationOrDomain(origin)) {
                continue;
            }
            String originDomain = ArchitectureRulesSupport.domainOf(origin);
            origin.getDirectDependenciesFromSelf().stream()
                    .map(Dependency::getTargetClass)
                    .filter(target -> target.getPackageName().contains(".contracts.event"))
                    .filter(target -> !originDomain.equals(ArchitectureRulesSupport.domainOf(target)))
                    .map(target -> origin.getFullName() + " -> " + target.getFullName())
                    .forEach(violations::add);
        }

        assertThat(violations)
                .as("foreign asynchronous contracts must be converted by inbound infrastructure adapters")
                .isEmpty();
    }

    @Test
    void publishedEventEnvelopesMustUseJsonNodePayloads() {
        List<String> violations = productionClasses.stream()
                .filter(javaClass -> EVENT_ENVELOPES.contains(javaClass.getFullName()))
                .flatMap(javaClass -> javaClass.getFields().stream())
                .filter(field -> field.getName().equals("payload"))
                .filter(field -> !field.getRawType().isEquivalentTo(JsonNode.class))
                .map(field -> field.getOwner().getFullName() + ".payload has type " + field.getRawType().getFullName())
                .sorted()
                .toList();

        assertThat(productionClasses.stream()
                .filter(javaClass -> EVENT_ENVELOPES.contains(javaClass.getFullName())))
                .as("all published owner envelopes must remain discoverable")
                .hasSize(EVENT_ENVELOPES.size());
        assertThat(violations)
                .as("published event envelopes must not regress to Object payload")
                .isEmpty();
    }

    @Test
    void migratedAggregatesMustNotExposePublicSetters() {
        List<String> setters = List.of(MarketOrder.class, WalletAccount.class).stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> method.getName().startsWith("set"))
                .map(Method::toGenericString)
                .sorted()
                .toList();

        assertThat(setters)
                .as("migrated aggregates must only change through domain behavior")
                .isEmpty();
    }

    private boolean isApplicationOrDomain(JavaClass javaClass) {
        return ArchitectureRulesSupport.residesInLayer(javaClass, Set.of("application", "domain"));
    }

    private boolean isSpringDaoOrTransactionImplementation(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.startsWith("org.springframework.dao")
                || (packageName.startsWith("org.springframework.transaction")
                && !packageName.startsWith("org.springframework.transaction.annotation"));
    }
}
