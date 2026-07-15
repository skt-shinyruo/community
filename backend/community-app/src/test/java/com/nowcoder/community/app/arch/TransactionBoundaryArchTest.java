package com.nowcoder.community.app.arch;

import com.nowcoder.community.growth.application.UserLevelApplicationService;
import com.nowcoder.community.growth.application.command.UpdateUserLevelConfigCommand;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.command.CreateMarketOrderCommand;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.application.UserModerationApplicationService;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.application.command.UpdateUserRoleCommand;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryArchTest {

    private static final String SPRING_TRANSACTIONAL =
            "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TRANSACTIONAL =
            "jakarta.transaction.Transactional";

    @Test
    void infrastructureMustNotOwnTransactionalBoundaries() {
        List<String> violations = new ArrayList<>();
        for (JavaClass javaClass : new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.nowcoder.community")) {
            if (!isInfrastructure(javaClass)) {
                continue;
            }
            if (isTransactional(javaClass)) {
                violations.add(javaClass.getFullName() + " is annotated with @Transactional");
            }
            javaClass.getMethods().stream()
                    .filter(this::isTransactional)
                    .map(method -> method.getFullName() + " is annotated with @Transactional")
                    .forEach(violations::add);
        }

        assertThat(violations)
                .as("transaction ownership belongs to ApplicationService, not infrastructure")
                .isEmpty();
    }

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
        assertTransactional(AdminUserApplicationService.class, "updateRole", UpdateUserRoleCommand.class);
        assertTransactional(UserCredentialApplicationService.class, "updatePassword", UUID.class, String.class);
        assertTransactional(UserModerationApplicationService.class, "applyModeration", ApplyUserModerationCommand.class);
        assertTransactional(
                LikeApplicationService.class,
                "cleanupDeletedContentLikes",
                CleanupDeletedContentLikesCommand.class
        );
    }

    private boolean isApplicationService(JavaClass javaClass) {
        return javaClass.getPackageName().contains(".application")
                && javaClass.getSimpleName().endsWith("ApplicationService");
    }

    private boolean isInfrastructure(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.contains(".infrastructure.")
                || packageName.endsWith(".infrastructure")
                || packageName.startsWith("com.nowcoder.community.infra.")
                || packageName.equals("com.nowcoder.community.infra");
    }

    private boolean isTransactional(com.tngtech.archunit.core.domain.properties.CanBeAnnotated element) {
        return element.isAnnotatedWith(SPRING_TRANSACTIONAL)
                || element.isAnnotatedWith(JAKARTA_TRANSACTIONAL);
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
