package com.nowcoder.community.social.follow;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.api.query.SocialFollowQueryApi;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.follow.dto.FollowItem;
import com.nowcoder.community.social.follow.dto.FollowRequest;
import com.nowcoder.community.infra.pagination.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.USER;
import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.social.exception.SocialErrorCode.CANNOT_FOLLOW_SELF;

@Service
public class FollowService implements SocialFollowQueryApi {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final SocialEventPublisher eventPublisher;
    private final BlockService blockService;

    public FollowService(FollowRepository followRepository, SocialEventPublisher eventPublisher, BlockService blockService) {
        this.followRepository = followRepository;
        this.eventPublisher = eventPublisher;
        this.blockService = blockService;
    }

    @Transactional
    public void follow(UUID actorUserId, FollowRequest request) {
        if (actorUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        int entityType = request.getEntityType();
        UUID entityId = request.getEntityId();
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "follow 仅支持 USER");
        }
        if (actorUserId == entityId) {
            throw new BusinessException(CANNOT_FOLLOW_SELF);
        }

        // 反骚扰：仅阻断“创建关注”副作用（幂等重复 follow 不额外阻断）
        boolean existed = followRepository.hasFollowed(actorUserId, entityType, entityId);
        if (!existed && blockService != null && blockService.isEitherBlocked(actorUserId, entityId)) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }

        long now = System.currentTimeMillis();
        boolean created = followRepository.follow(actorUserId, entityType, entityId, now);
        if (created) {
            FollowPayload payload = new FollowPayload();
            payload.setActorUserId(actorUserId);
            payload.setEntityType(entityType);
            payload.setEntityId(entityId);
            payload.setEntityUserId(entityId);
            payload.setCreateTime(Instant.ofEpochMilli(now));
            Runnable rollback = () -> followRepository.unfollow(actorUserId, entityType, entityId);
            boolean needsExplicitCompensation = followRepository.requiresExplicitCompensation();
            if (needsExplicitCompensation) {
                registerRollbackIfTxRolledBack(rollback);
            }
            try {
                eventPublisher.publishFollowCreated(payload);
            } catch (RuntimeException ex) {
                if (needsExplicitCompensation) {
                    try {
                        rollback.run();
                    } catch (RuntimeException rollbackEx) {
                        log.warn("[follow] rollback failed after publish error (entityType={}, entityId={}, actorUserId={}): {}",
                                entityType, entityId, actorUserId, rollbackEx.toString());
                    }
                }
                throw ex;
            }
        }
    }

    private void registerRollbackIfTxRolledBack(Runnable rollback) {
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
                    log.warn("[follow] rollback failed after tx rollback: {}", ex.toString());
                }
            }
        });
    }

    @Transactional
    public void unfollow(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            throw new BusinessException(INVALID_ARGUMENT, "unfollow 仅支持 USER");
        }
        followRepository.unfollow(actorUserId, entityType, entityId);
    }

    @Override
    public boolean hasFollowed(UUID actorUserId, int entityType, UUID entityId) {
        if (actorUserId == null || entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return false;
        }
        return followRepository.hasFollowed(actorUserId, entityType, entityId);
    }

    @Override
    public long followeeCount(UUID userId, int entityType) {
        if (userId == null || entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return 0;
        }
        return followRepository.countFollowees(userId, entityType);
    }

    @Override
    public long followerCount(int entityType, UUID entityId) {
        if (entityType <= 0 || entityId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        if (entityType != USER) {
            return 0;
        }
        return followRepository.countFollowers(entityType, entityId);
    }

    public List<FollowItem> listFollowees(UUID userId, int entityType, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        if (entityType != USER) {
            return List.of();
        }
        return followRepository.listFollowees(userId, entityType, Pagination.safeOffset(p, s), s);
    }

    public List<FollowItem> listFollowers(int entityType, UUID entityId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        if (entityType != USER) {
            return List.of();
        }
        return followRepository.listFollowers(entityType, entityId, Pagination.safeOffset(p, s), s);
    }
}
