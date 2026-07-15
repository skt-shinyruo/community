package com.nowcoder.community.drive.infrastructure.transaction;

import com.nowcoder.community.drive.application.DriveTransactionOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public class SpringDriveTransactionOperations implements DriveTransactionOperations {

    private final TransactionTemplate transactionTemplate;

    public SpringDriveTransactionOperations(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T requiresNew(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return transactionTemplate.execute(status -> action.get());
    }
}
