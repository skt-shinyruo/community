package com.nowcoder.community.auth.application.port;

public interface PasswordResetTransactionCompletion {

    void afterRollback(Runnable action);
}
