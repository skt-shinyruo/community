package com.nowcoder.community.social.like;

import com.nowcoder.community.social.api.event.payload.LikePayload;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.social.event.SocialEventPublisher;
import com.nowcoder.community.social.like.dto.LikeRequest;
import com.nowcoder.community.social.like.dto.LikeResponse;
import com.nowcoder.community.social.service.ContentEntityResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.nowcoder.community.common.domain.EntityTypes.COMMENT;
import static com.nowcoder.community.common.domain.EntityTypes.POST;
import static com.nowcoder.community.common.domain.EntityTypes.USER;
import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeRepository likeRepository;
    private final SocialEventPublisher eventPublisher;
    private final ContentEntityResolver contentEntityResolver;
    private final BlockService blockService;
    private final boolean redisStorage;

    public LikeService(
            LikeRepository likeRepository,
            SocialEventPublisher eventPublisher,
            ContentEntityResolver contentEntityResolver,
            BlockService blockService
    ) {
        this(likeRepository, eventPublisher, contentEntityResolver, blockService, "memory");
    }

    @Autowired
    public LikeService(
            LikeRepository likeRepository,
            SocialEventPublisher eventPublisher,
            ContentEntityResolver contentEntityResolver,
            BlockService blockService,
            @Value("${social.storage:db}") String storage
    ) {
        this.likeRepository = likeRepository;
        this.eventPublisher = eventPublisher;
        this.contentEntityResolver = contentEntityResolver;
        this.blockService = blockService;
        this.redisStorage = "redis".equalsIgnoreCase(storage);
    }

    @Transactional
    public LikeResponse setLike(int actorUserId, LikeRequest request) {
        if (actorUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        int entityType = request.getEntityType();
        int entityId = request.getEntityId();
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }

        boolean existed = likeRepository.isLiked(actorUserId, entityType, entityId);
        // 兼容 toggle / set 两种语义：
        // - toggle：不传 liked 时翻转当前状态
        // - set：liked=true/false 代表目标状态（幂等）
        boolean liked = (request.getLiked() == null) ? !existed : Boolean.TRUE.equals(request.getLiked());

        // 反骚扰：仅阻断“创建点赞”副作用（like existed=false -> liked=true）。
        // 允许取消点赞（清理自身状态），也允许幂等重复 set=true（不产生新副作用）。
        ResolvedEntity resolvedForCreate = null;
        if (liked && !existed) {
            resolvedForCreate = resolveEntityForPayload(entityType, entityId);
            if (resolvedForCreate.entityUserId > 0
                    && blockService != null
                    && blockService.isEitherBlocked(actorUserId, resolvedForCreate.entityUserId)) {
                throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
            }
        }

        if (redisStorage) {
            handleSetLikeForRedisStorage(actorUserId, entityType, entityId, existed, liked, resolvedForCreate);
        } else {
            handleSetLikeForNonRedisStorage(actorUserId, entityType, entityId, liked, resolvedForCreate);
        }

        LikeResponse resp = new LikeResponse();
        resp.setLiked(likeRepository.isLiked(actorUserId, entityType, entityId));
        resp.setLikeCount(likeRepository.countEntityLikes(entityType, entityId));
        return resp;
    }

    private void handleSetLikeForNonRedisStorage(
            int actorUserId,
            int entityType,
            int entityId,
            boolean liked,
            ResolvedEntity resolvedForCreate
    ) {
        if (liked) {
            boolean added = likeRepository.addLike(actorUserId, entityType, entityId);
            if (!added) {
                return;
            }
            ResolvedEntity resolved = resolvedForCreate == null ? resolveEntityForPayload(entityType, entityId) : resolvedForCreate;
            if (resolved.entityUserId > 0) {
                likeRepository.incrementUserLikeCount(resolved.entityUserId, 1);
            }
            LikePayload payload = new LikePayload();
            payload.setActorUserId(actorUserId);
            payload.setEntityType(entityType);
            payload.setEntityId(entityId);
            payload.setEntityUserId(resolved.entityUserId <= 0 ? null : resolved.entityUserId);
            payload.setPostId(resolved.postId <= 0 ? null : resolved.postId);
            payload.setCreateTime(Instant.now());
            eventPublisher.publishLikeCreated(payload);
            return;
        }

        boolean removed = likeRepository.removeLike(actorUserId, entityType, entityId);
        if (!removed) {
            return;
        }
        ResolvedEntity resolved = resolveEntityForPayload(entityType, entityId);
        if (resolved.entityUserId > 0) {
            likeRepository.incrementUserLikeCount(resolved.entityUserId, -1);
        }
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(entityType);
        payload.setEntityId(entityId);
        payload.setEntityUserId(resolved.entityUserId <= 0 ? null : resolved.entityUserId);
        payload.setPostId(resolved.postId <= 0 ? null : resolved.postId);
        payload.setCreateTime(Instant.now());
        eventPublisher.publishLikeRemoved(payload);
    }

    private void handleSetLikeForRedisStorage(
            int actorUserId,
            int entityType,
            int entityId,
            boolean existed,
            boolean liked,
            ResolvedEntity resolvedForCreate
    ) {
        // 幂等快路径：避免在 Redis 模式下对 idempotent no-op 请求额外 resolve（尤其是压测场景）。
        if (liked == existed) {
            return;
        }

        ResolvedEntity resolved = resolvedForCreate == null ? resolveEntityForPayload(entityType, entityId) : resolvedForCreate;
        int entityUserId = resolved.entityUserId;

        boolean changed = likeRepository.setLike(actorUserId, entityType, entityId, entityUserId, liked);
        if (!changed) {
            return;
        }

        Runnable rollback = () -> likeRepository.setLike(actorUserId, entityType, entityId, entityUserId, !liked);
        registerRollbackIfTxRolledBack(rollback);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(entityType);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId <= 0 ? null : entityUserId);
        payload.setPostId(resolved.postId <= 0 ? null : resolved.postId);
        payload.setCreateTime(Instant.now());
        try {
            if (liked) {
                eventPublisher.publishLikeCreated(payload);
            } else {
                eventPublisher.publishLikeRemoved(payload);
            }
        } catch (RuntimeException ex) {
            try {
                rollback.run();
            } catch (RuntimeException rollbackEx) {
                log.warn("[like] rollback failed after publish error (entityType={}, entityId={}, actorUserId={}, liked={}): {}",
                        entityType, entityId, actorUserId, liked, rollbackEx.toString());
            }
            throw ex;
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
                    log.warn("[like] rollback failed after tx rollback: {}", ex.toString());
                }
            }
        });
    }

    private ResolvedEntity resolveEntityForPayload(int entityType, int entityId) {
        if (entityType == USER) {
            // user 类型的 like（如未来启用）：由 entityId 自洽推导，避免信任客户端注入字段。
            ResolvedEntity r = new ResolvedEntity();
            r.entityUserId = entityId;
            r.postId = 0;
            return r;
        }
        if (entityType == POST || entityType == COMMENT) {
            ContentEntityResolver.ResolvedEntity resolved = contentEntityResolver.resolve(entityType, entityId);
            ResolvedEntity r = new ResolvedEntity();
            r.entityUserId = resolved.getEntityUserId();
            r.postId = resolved.getPostId();
            return r;
        }
        throw new BusinessException(INVALID_ARGUMENT, "entityType 不支持");
    }

    private static class ResolvedEntity {
        private int entityUserId;
        private int postId;
    }

    public boolean isLiked(int actorUserId, int entityType, int entityId) {
        if (actorUserId <= 0 || entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.isLiked(actorUserId, entityType, entityId);
    }

    public long count(int entityType, int entityId) {
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "参数错误");
        }
        return likeRepository.countEntityLikes(entityType, entityId);
    }

    public long userLikeCount(int userId) {
        if (userId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
        return likeRepository.getUserLikeCount(userId);
    }

    public Map<Integer, Long> counts(int entityType, List<Integer> entityIds) {
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.countEntityLikesBatch(entityType, entityIds);
    }

    public Map<Integer, Boolean> statuses(int actorUserId, int entityType, List<Integer> entityIds) {
        if (actorUserId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "actorUserId 非法");
        }
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        return likeRepository.likedStatusesBatch(actorUserId, entityType, entityIds);
    }
}
