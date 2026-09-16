package com.nowcoder.community.drive.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public class DriveTransactionOperations {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T requiresNew(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return action.get();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNew(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        requiresNew(() -> {
            action.run();
            return null;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public <T> T readOnly(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        return action.get();
    }
}
