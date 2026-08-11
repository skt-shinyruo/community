package com.nowcoder.community.wallet.infrastructure.retry;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WalletDeadlockRetryProperties.class)
public class WalletDeadlockRetryConfiguration {

    static final int RETRY_ADVISOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    @Bean
    DefaultPointcutAdvisor walletDeadlockRetryAdvisor(WalletDeadlockRetryProperties properties) {
        return createWalletDeadlockRetryAdvisor(properties);
    }

    static DefaultPointcutAdvisor createWalletDeadlockRetryAdvisor(WalletDeadlockRetryProperties properties) {
        Pointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                if (targetClass == null
                        || !targetClass.getPackageName().startsWith("com.nowcoder.community.wallet.application")) {
                    return false;
                }
                Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
                return AnnotatedElementUtils.hasAnnotation(specific, Transactional.class)
                        || AnnotatedElementUtils.hasAnnotation(targetClass, Transactional.class);
            }
        };
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
                new WalletDeadlockRetryInterceptor(properties));
        advisor.setOrder(RETRY_ADVISOR_ORDER);
        return advisor;
    }

    static final class WalletDeadlockRetryInterceptor implements MethodInterceptor {

        private final WalletDeadlockRetryProperties properties;

        WalletDeadlockRetryInterceptor(WalletDeadlockRetryProperties properties) {
            this.properties = properties;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            if (org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()) {
                return invocation.proceed();
            }
            int maxAttempts = properties.normalizedMaxAttempts();
            Duration backoff = properties.normalizedBackoff();
            Throwable last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    return proceedClone(invocation);
                } catch (Throwable failure) {
                    if (!isRetryable(failure) || attempt == maxAttempts) {
                        throw failure;
                    }
                    last = failure;
                    sleep(backoff);
                }
            }
            throw last == null ? new IllegalStateException("wallet retry exhausted") : last;
        }

        private Object proceedClone(MethodInvocation invocation) throws Throwable {
            if (invocation instanceof ProxyMethodInvocation proxyInvocation) {
                return proxyInvocation.invocableClone().proceed();
            }
            return invocation.proceed();
        }

        private boolean isRetryable(Throwable failure) {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof PessimisticLockingFailureException) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }

        private void sleep(Duration delay) throws InterruptedException {
            if (delay.isZero()) {
                return;
            }
            long millis = delay.toMillis();
            int nanos = (int) (delay.minusMillis(millis).toNanos());
            try {
                Thread.sleep(millis, nanos);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
    }
}
