package com.nowcoder.community.common.idempotency;

/**
 * 能够确认自身存储资源是否已加入当前 Spring 事务的幂等存储。
 */
public interface TransactionalIdempotencyStore extends IdempotencyStore {

    boolean isEnlistedInCurrentTransaction();
}
