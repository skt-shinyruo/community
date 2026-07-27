package com.nowcoder.community.oss.arch;

import com.nowcoder.community.oss.application.ObjectLifecycleApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionBoundaryArchTest {

    @Test
    void lifecycleEntryPointsMustNotOpenTransactionsAroundExternalDelete() throws Exception {
        assertNotTransactional(ObjectLifecycleApplicationService.class.getMethod(
                "deleteObject", com.nowcoder.community.oss.application.command.DeleteObjectCommand.class));
        assertNotTransactional(ObjectLifecycleApplicationService.class.getMethod(
                "deleteInternalObject",
                com.nowcoder.community.oss.application.command.DeleteObjectCommand.class,
                String.class));
        assertThat(ObjectLifecycleApplicationService.class.isAnnotationPresent(Transactional.class))
                .as("lifecycle service must not put storage delete inside a class-level transaction")
                .isFalse();
    }

    private void assertNotTransactional(Method method) {
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as(method + " must orchestrate storage deletion outside a database transaction")
                .isFalse();
    }
}
