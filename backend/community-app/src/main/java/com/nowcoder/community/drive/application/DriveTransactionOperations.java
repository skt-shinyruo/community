package com.nowcoder.community.drive.application;

import java.util.function.Supplier;

public interface DriveTransactionOperations {

    <T> T requiresNew(Supplier<T> action);

    default void requiresNew(Runnable action) {
        requiresNew(() -> {
            action.run();
            return null;
        });
    }
}

enum DirectDriveTransactionOperations implements DriveTransactionOperations {
    INSTANCE;

    @Override
    public <T> T requiresNew(Supplier<T> action) {
        return action.get();
    }
}
