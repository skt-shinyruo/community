package com.nowcoder.community.drive.infrastructure.transaction;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringDriveTransactionOperationsTest {

    @Test
    void requiresNewShouldReturnActionResultAndCommitWithRequiresNewPropagation() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        SpringDriveTransactionOperations operations = new SpringDriveTransactionOperations(transactionManager);

        String result = operations.requiresNew(() -> "completed");

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(result).isEqualTo("completed");
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(transactionStatus);
    }

    @Test
    void requiresNewShouldRollBackAndPropagateActionFailure() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        SpringDriveTransactionOperations operations = new SpringDriveTransactionOperations(transactionManager);
        IllegalStateException failure = new IllegalStateException("completion failed");
        Supplier<String> failingAction = () -> {
            throw failure;
        };

        assertThatThrownBy(() -> operations.requiresNew(failingAction)).isSameAs(failure);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);
    }
}
