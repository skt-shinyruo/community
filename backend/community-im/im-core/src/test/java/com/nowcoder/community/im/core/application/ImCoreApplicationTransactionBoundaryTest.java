package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.core.policy.PrivateMessagePolicyVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ImCoreApplicationTransactionBoundaryTest {

    @Test
    void privateMessageEntryPointMustSuspendCallerTransactions() throws NoSuchMethodException {
        Method persist = PrivateMessageApplicationService.class.getDeclaredMethod(
                "persist",
                SendPrivateTextCommand.class
        );
        Transactional transactional = persist.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("private-message orchestration needs an explicit non-transactional outer boundary")
                .isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void policyVerifierMustNotBeInjectedIntoTransactionalOperations() {
        boolean verifierDependency = Arrays.stream(PrivateMessageTransactionOperations.class.getDeclaredFields())
                .anyMatch(field -> PrivateMessagePolicyVerifier.class.isAssignableFrom(field.getType()));

        assertThat(verifierDependency)
                .as("remote policy verification must stay outside application transaction operations")
                .isFalse();
    }

    @Test
    void privateMessageDatabaseOperationsMustRemainRequiresNew() {
        assertRequiresNew("findExisting");
        assertRequiresNew("persist");
    }

    private static void assertRequiresNew(String methodName) {
        Method method = Arrays.stream(PrivateMessageTransactionOperations.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("PrivateMessageTransactionOperations." + methodName + " must own a short transaction")
                .isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
