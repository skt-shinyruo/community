package com.nowcoder.community.drive.application;

import java.util.function.Supplier;

public interface DriveTransactionOperations {

    <T> T requiresNew(Supplier<T> action);

    default <T> T readOnly(Supplier<T> action) {
        return requiresNew(action);
    }

    default void requiresNew(Runnable action) {
        requiresNew(() -> {
            action.run();
            return null;
        });
    }
}