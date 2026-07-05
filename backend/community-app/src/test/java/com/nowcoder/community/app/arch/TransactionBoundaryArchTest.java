package com.nowcoder.community.app.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryArchTest {

    @Test
    void applicationServicesMustNotSelfInvokeTransactionalMethods() {
        List<String> violations = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")
                .stream()
                .filter(this::isApplicationService)
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .filter(this::isSameClassCall)
                .filter(this::targetsTransactionalBoundary)
                .map(this::describe)
                .sorted()
                .toList();

        assertThat(violations)
                .as("@Transactional self-invocation bypasses Spring AOP proxies")
                .isEmpty();
    }

    private boolean isApplicationService(JavaClass javaClass) {
        return javaClass.getPackageName().contains(".application")
                && javaClass.getSimpleName().endsWith("ApplicationService");
    }

    private boolean isSameClassCall(JavaMethodCall call) {
        return call.getOriginOwner().equals(call.getTargetOwner());
    }

    private boolean targetsTransactionalBoundary(JavaMethodCall call) {
        boolean methodAnnotated = call.getTarget()
                .resolveMember()
                .filter(member -> member.isAnnotatedWith(Transactional.class))
                .isPresent();
        return methodAnnotated || call.getTargetOwner().isAnnotatedWith(Transactional.class);
    }

    private String describe(JavaMethodCall call) {
        return call.getOrigin().getFullName()
                + " calls "
                + call.getTarget().getFullName()
                + " at "
                + call.getSourceCodeLocation();
    }
}
