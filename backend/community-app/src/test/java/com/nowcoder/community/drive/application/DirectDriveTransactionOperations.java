package com.nowcoder.community.drive.application;

import java.util.function.Supplier;

enum DirectDriveTransactionOperations implements DriveTransactionOperations {
    INSTANCE;

    @Override
    public <T> T requiresNew(Supplier<T> action) {
        return action.get();
    }
}
