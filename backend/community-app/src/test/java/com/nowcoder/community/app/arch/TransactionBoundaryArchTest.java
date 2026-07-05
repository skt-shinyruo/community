package com.nowcoder.community.app.arch;

import com.nowcoder.community.growth.application.UserLevelApplicationService;
import com.nowcoder.community.growth.application.command.UpdateUserLevelConfigCommand;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.command.CreateMarketOrderCommand;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService;
import com.nowcoder.community.wallet.application.command.CreateRechargeCommand;
import com.nowcoder.community.wallet.application.command.CreateTransferCommand;
import com.nowcoder.community.wallet.application.command.CreateWithdrawCommand;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

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

    @Test
    void controllerFacingApplicationEntryPointsMustRemainTransactional() throws NoSuchMethodException {
        assertTransactional(WalletRechargeApplicationService.class, "recharge", CreateRechargeCommand.class);
        assertTransactional(WalletWithdrawApplicationService.class, "withdraw", CreateWithdrawCommand.class);
        assertTransactional(WalletTransferApplicationService.class, "transfer", CreateTransferCommand.class);
        assertTransactional(MarketOrderApplicationService.class, "createOrder", CreateMarketOrderCommand.class);
        assertTransactional(UserLevelApplicationService.class, "updateConfig", UUID.class, UpdateUserLevelConfigCommand.class);
        assertTransactional(MarketWalletActionRecoveryApplicationService.class, "reconcileOnce", int.class);
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

    private void assertTransactional(Class<?> owner, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = owner.getMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as(owner.getSimpleName() + "." + methodName + " must remain a public transactional entry point")
                .isTrue();
    }
}
