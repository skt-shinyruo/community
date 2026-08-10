package com.nowcoder.community.app.arch;

import com.nowcoder.community.growth.application.UserLevelApplicationService;
import com.nowcoder.community.market.application.MarketOrderApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService;
import com.nowcoder.community.market.application.MarketWalletActionRecoveryTransactionOperations;
import com.nowcoder.community.content.application.PostReadTransactionOperations;
import com.nowcoder.community.content.application.PostHotFeedProjectionApplicationService;
import com.nowcoder.community.content.application.PostHotFeedProjectionTransactionOperations;
import com.nowcoder.community.content.application.CommentDeletionTransactionOperations;
import com.nowcoder.community.content.application.CommentThreadCleanupApplicationService;
import com.nowcoder.community.content.domain.model.CommentDeletion;
import com.nowcoder.community.social.application.LikeApplicationService;
import com.nowcoder.community.social.application.LikeCleanupTransactionOperations;
import com.nowcoder.community.social.application.command.CleanupDeletedContentLikesCommand;
import com.nowcoder.community.user.application.AdminUserApplicationService;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserAvatarTransactionOperations;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.application.UserModerationApplicationService;
import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.wallet.application.WalletRechargeApplicationService;
import com.nowcoder.community.wallet.application.WalletTransferApplicationService;
import com.nowcoder.community.wallet.application.WalletWithdrawApplicationService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
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
        assertTransactional(
                WalletRechargeApplicationService.class,
                "recharge",
                WalletRechargeApplicationService.CreateRechargeCommand.class
        );
        assertTransactional(
                WalletWithdrawApplicationService.class,
                "withdraw",
                WalletWithdrawApplicationService.CreateWithdrawCommand.class
        );
        assertTransactional(
                WalletTransferApplicationService.class,
                "transfer",
                WalletTransferApplicationService.CreateTransferCommand.class
        );
        assertTransactional(
                MarketOrderApplicationService.class,
                "createOrder",
                MarketOrderApplicationService.CreateOrderCommand.class
        );
        assertTransactional(
                UserLevelApplicationService.class,
                "updateConfig",
                UUID.class,
                UserLevelApplicationService.UpdateConfigCommand.class
        );
        assertTransactional(
                AdminUserApplicationService.class,
                "updateRole",
                AdminUserApplicationService.UpdateRoleCommand.class
        );
        assertTransactional(UserCredentialApplicationService.class, "updatePassword", UUID.class, String.class);
        assertTransactional(
                UserModerationApplicationService.class,
                "applyModeration",
                UserModerationActionApi.ApplyModerationCommand.class
        );
        assertTransactionalWithPropagation(
                UserModerationApplicationService.class,
                "assertActiveModerationActor",
                Propagation.MANDATORY,
                UUID.class
        );
    }

    @Test
    void bulkCleanupAndRemoteIoMustRemainOutsideLongDatabaseTransactions() throws NoSuchMethodException {
        assertTransactionalWithPropagation(
                CommentDeletionTransactionOperations.class,
                "deleteRoot",
                Propagation.REQUIRED,
                CommentDeletion.class,
                UUID.class
        );
        assertTransactionalWithPropagation(
                CommentDeletionTransactionOperations.class,
                "deleteSingle",
                Propagation.REQUIRED,
                CommentDeletion.class,
                UUID.class
        );
        assertTransactionalWithPropagation(
                CommentDeletionTransactionOperations.class,
                "deleteReplyBatch",
                Propagation.REQUIRES_NEW,
                UUID.class,
                UUID.class,
                UUID.class,
                String.class,
                Date.class,
                int.class
        );
        assertNotTransactional(
                CommentThreadCleanupApplicationService.class,
                "reconcile",
                int.class
        );
        assertNotTransactional(MarketWalletActionRecoveryApplicationService.class, "reconcileOnce", int.class);
        assertNotTransactional(
                MarketWalletActionRecoveryApplicationService.class,
                "recoverExpiredProcessing",
                java.time.Instant.class
        );
        assertTransactionalWithPropagation(
                MarketWalletActionRecoveryTransactionOperations.class,
                "recoverExpiredProcessing",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                com.nowcoder.community.market.domain.model.MarketWalletAction.class,
                Date.class,
                int.class
        );
        assertTransactionalWithPropagation(
                MarketWalletActionRecoveryTransactionOperations.class,
                "reconcileWalletTxnAction",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                UUID.class
        );
        assertTransactionalWithPropagation(
                MarketWalletActionRecoveryTransactionOperations.class,
                "reconcilePendingOrder",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                UUID.class,
                Date.class,
                int.class
        );
        assertNotTransactional(
                LikeApplicationService.class,
                "cleanupDeletedContentLikes",
                CleanupDeletedContentLikesCommand.class
        );
        assertTransactionalWithPropagation(
                LikeCleanupTransactionOperations.class,
                "persistDeletionFence",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                CleanupDeletedContentLikesCommand.class
        );
        assertTransactionalWithPropagation(
                LikeCleanupTransactionOperations.class,
                "cleanupBatch",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                int.class,
                UUID.class,
                int.class
        );
        assertTransactionalWithPropagation(
                LikeCleanupTransactionOperations.class,
                "cleanupCommentLikesByPostBatch",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                UUID.class,
                int.class
        );
        assertNotTransactional(
                UserAvatarApplicationService.class,
                "updateAvatar",
                UUID.class,
                UUID.class,
                UUID.class
        );
        assertTransactional(
                UserAvatarTransactionOperations.class,
                "updateHeaderUrl",
                UUID.class,
                String.class
        );
        assertNotTransactional(
                PostHotFeedProjectionApplicationService.class,
                "project",
                PostHotFeedProjectionApplicationService.ProjectPostHotFeedCommand.class
        );
        assertTransactionalWithPropagation(
                PostHotFeedProjectionTransactionOperations.class,
                "updateScore",
                org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
                UUID.class,
                double.class,
                long.class
        );
    }

    @Test
    void postMultiTableReadsMustUseRepeatableReadTransactions() throws NoSuchMethodException {
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "listPosts",
                int.class, int.class, int.class, UUID.class, String.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "listSubscribedPosts",
                UUID.class, List.class, int.class, int.class, int.class, UUID.class, String.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "listPostsByUser",
                UUID.class, int.class, int.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "listPostsByIds", List.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "getDetail", UUID.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "getProjectionAllowDeleted", UUID.class);
        assertRepeatableReadOnly(PostReadTransactionOperations.class, "scanPosts", UUID.class, int.class);
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

    private void assertNotTransactional(Class<?> owner, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getMethod(methodName, parameterTypes);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as(owner.getSimpleName() + "." + methodName + " must remain outside a database transaction")
                .isFalse();
    }

    private void assertTransactionalWithPropagation(
            Class<?> owner,
            String methodName,
            org.springframework.transaction.annotation.Propagation propagation,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        Method method = owner.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as(owner.getSimpleName() + "." + methodName + " must own a short transaction")
                .isNotNull();
        assertThat(transactional.propagation())
                .as(owner.getSimpleName() + "." + methodName + " propagation")
                .isEqualTo(propagation);
    }

    private void assertRepeatableReadOnly(Class<?> owner, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as(owner.getSimpleName() + "." + methodName + " must own a consistent read snapshot")
                .isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }
}
