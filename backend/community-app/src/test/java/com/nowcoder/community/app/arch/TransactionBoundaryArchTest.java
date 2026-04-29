package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryArchTest {

    private static final Set<String> LOCKING_APPLICATION_SERVICES = Set.of(
            "com.nowcoder.community.growth.application.TaskProgressApplicationService",
            "com.nowcoder.community.market.application.MarketOrderAutoConfirmApplicationService",
            "com.nowcoder.community.market.application.MarketOrderAutoConfirmSingleOrderApplicationService"
    );

    @Test
    void lockingApplicationServicesMustNotSelfInvokeTransactionalMethods() {
        List<String> violations = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")
                .stream()
                .filter(javaClass -> LOCKING_APPLICATION_SERVICES.contains(javaClass.getName()))
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .filter(this::isSameClassCall)
                .filter(this::targetsTransactionalMethod)
                .map(this::describe)
                .sorted()
                .toList();

        assertThat(violations)
                .as("@Transactional self-invocation bypasses Spring AOP proxies")
                .isEmpty();
    }

    private boolean isSameClassCall(JavaMethodCall call) {
        return call.getOriginOwner().equals(call.getTargetOwner());
    }

    private boolean targetsTransactionalMethod(JavaMethodCall call) {
        return call.getTarget()
                .resolveMember()
                .filter(member -> member.isAnnotatedWith(Transactional.class))
                .isPresent();
    }

    private String describe(JavaMethodCall call) {
        return call.getOrigin().getFullName()
                + " calls "
                + call.getTarget().getFullName()
                + " at "
                + call.getSourceCodeLocation();
    }
}
