// 拉黑业务服务：实现拉黑/解除拉黑/状态查询/内部关系查询（用于反骚扰与写路径校验）。
package com.nowcoder.community.social.block;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.event.SocialEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.social.exception.SocialErrorCode.CANNOT_BLOCK_SELF;

@Service
public class BlockService implements SocialBlockQueryApi {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    private final BlockRepository repository;
    private final SocialEventPublisher eventPublisher;

    public BlockService(BlockRepository repository, SocialEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void block(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        if (userId == targetUserId) {
            throw new BusinessException(CANNOT_BLOCK_SELF);
        }
        boolean changed = repository.block(userId, targetUserId);
        if (!changed) {
            return;
        }

        Runnable rollback = () -> repository.unblock(userId, targetUserId);
        registerRollbackIfTxRolledBack(rollback);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(userId);
        payload.setBlockedUserId(targetUserId);
        payload.setBlocked(Boolean.TRUE);
        try {
            eventPublisher.publishBlockRelationChanged(payload);
        } catch (RuntimeException ex) {
            try {
                rollback.run();
            } catch (RuntimeException rollbackEx) {
                log.warn("[block] rollback failed after publish error (userId={}, targetUserId={}): {}",
                        userId, targetUserId, rollbackEx.toString());
            }
            throw ex;
        }
    }

    @Transactional
    public void unblock(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId/targetUserId 非法");
        }
        boolean changed = repository.unblock(userId, targetUserId);
        if (!changed) {
            return;
        }

        Runnable rollback = () -> repository.block(userId, targetUserId);
        registerRollbackIfTxRolledBack(rollback);

        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(userId);
        payload.setBlockedUserId(targetUserId);
        payload.setBlocked(Boolean.FALSE);
        try {
            eventPublisher.publishBlockRelationChanged(payload);
        } catch (RuntimeException ex) {
            try {
                rollback.run();
            } catch (RuntimeException rollbackEx) {
                log.warn("[block] rollback failed after publish error (userId={}, targetUserId={}): {}",
                        userId, targetUserId, rollbackEx.toString());
            }
            throw ex;
        }
    }

    public boolean hasBlocked(int userId, int targetUserId) {
        if (userId <= 0 || targetUserId <= 0) {
            return false;
        }
        return repository.hasBlocked(userId, targetUserId);
    }

    public boolean isEitherBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0) {
            return false;
        }
        if (userIdA == userIdB) {
            return false;
        }
        return repository.hasBlocked(userIdA, userIdB) || repository.hasBlocked(userIdB, userIdA);
    }

    public List<Integer> listBlockedUserIds(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return repository.listBlockedUserIds(userId);
    }

    private void registerRollbackIfTxRolledBack(Runnable rollback) {
        if (rollback == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    rollback.run();
                } catch (RuntimeException ex) {
                    log.warn("[block] rollback failed after tx rollback: {}", ex.toString());
                }
            }
        });
    }
}
