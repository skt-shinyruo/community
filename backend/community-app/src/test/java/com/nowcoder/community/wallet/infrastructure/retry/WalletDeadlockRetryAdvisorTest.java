package com.nowcoder.community.wallet.infrastructure.retry;

import com.nowcoder.community.wallet.application.WalletDeadlockRetryTestTarget;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletDeadlockRetryAdvisorTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void retryShouldReenterEveryDownstreamAdvisorForEachWholeTransactionAttempt() {
        WalletDeadlockRetryTestTarget target = new WalletDeadlockRetryTestTarget(
                2,
                new CannotAcquireLockException("deadlock")
        );
        AtomicInteger transactionAttempts = new AtomicInteger();
        WalletDeadlockRetryTestTarget proxy = proxy(target, transactionAttempts, properties(3));

        assertThat(proxy.execute()).isEqualTo("ok");
        assertThat(target.attempts()).isEqualTo(3);
        assertThat(transactionAttempts).hasValue(3);
    }

    @Test
    void retryShouldStopAtConfiguredBoundAndPropagateTheLastDeadlock() {
        WalletDeadlockRetryTestTarget target = new WalletDeadlockRetryTestTarget(
                Integer.MAX_VALUE,
                new CannotAcquireLockException("deadlock")
        );
        AtomicInteger transactionAttempts = new AtomicInteger();
        WalletDeadlockRetryTestTarget proxy = proxy(target, transactionAttempts, properties(2));

        assertThatThrownBy(proxy::execute)
                .isInstanceOf(CannotAcquireLockException.class)
                .hasMessageContaining("deadlock");
        assertThat(target.attempts()).isEqualTo(2);
        assertThat(transactionAttempts).hasValue(2);
    }

    @Test
    void retryShouldNotHandleNonLockFailuresOrRetryInsideAnExistingTransaction() {
        WalletDeadlockRetryTestTarget nonLockTarget = new WalletDeadlockRetryTestTarget(
                1,
                new IllegalStateException("boom")
        );
        AtomicInteger nonLockTransactions = new AtomicInteger();
        WalletDeadlockRetryTestTarget nonLockProxy = proxy(nonLockTarget, nonLockTransactions, properties(3));

        assertThatThrownBy(nonLockProxy::execute).isInstanceOf(IllegalStateException.class);
        assertThat(nonLockTarget.attempts()).isEqualTo(1);
        assertThat(nonLockTransactions).hasValue(1);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        WalletDeadlockRetryTestTarget nestedTarget = new WalletDeadlockRetryTestTarget(
                1,
                new CannotAcquireLockException("nested")
        );
        AtomicInteger nestedTransactions = new AtomicInteger();
        WalletDeadlockRetryTestTarget nestedProxy = proxy(nestedTarget, nestedTransactions, properties(3));

        assertThatThrownBy(nestedProxy::execute).isInstanceOf(CannotAcquireLockException.class);
        assertThat(nestedTarget.attempts()).isEqualTo(1);
        assertThat(nestedTransactions).hasValue(1);
    }

    private static WalletDeadlockRetryTestTarget proxy(
            WalletDeadlockRetryTestTarget target,
            AtomicInteger transactionAttempts,
            WalletDeadlockRetryProperties properties
    ) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(WalletDeadlockRetryConfiguration.createWalletDeadlockRetryAdvisor(properties));

        MethodInterceptor transactionInterceptor = invocation -> {
            transactionAttempts.incrementAndGet();
            return invocation.proceed();
        };
        DefaultPointcutAdvisor transactionAdvisor = new DefaultPointcutAdvisor(transactionInterceptor);
        transactionAdvisor.setOrder(WalletDeadlockRetryConfiguration.RETRY_ADVISOR_ORDER + 1);
        factory.addAdvisor(transactionAdvisor);
        return (WalletDeadlockRetryTestTarget) factory.getProxy();
    }

    private static WalletDeadlockRetryProperties properties(int maxAttempts) {
        WalletDeadlockRetryProperties properties = new WalletDeadlockRetryProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setBackoff(Duration.ZERO);
        return properties;
    }
}
