package com.nowcoder.community.social.service;

import com.nowcoder.community.common.api.ContentErrorCode;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.social.projection.ContentEntityProjection;
import com.nowcoder.community.social.projection.ContentEntityProjectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * 内容实体元信息解析器（social 写路径可信只读依赖）：
 * - 只读取本地投影（最终一致），避免跨服务同步依赖与依赖环
 * - 投影缺失/不完整时 fail-closed（快速失败），由事件回放/投影重建纠偏
 */
@Service
public class ContentEntityResolver {

    private final ContentEntityProjectionRepository projectionRepository;
    private final MeterRegistry meterRegistry;

    public ContentEntityResolver(
            ContentEntityProjectionRepository projectionRepository,
            MeterRegistry meterRegistry
    ) {
        this.projectionRepository = projectionRepository;
        this.meterRegistry = meterRegistry;
    }

    public ResolvedEntity resolve(int entityType, int entityId) {
        if (entityId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityType 不支持");
        }

        ContentEntityProjection p = projectionRepository.find(entityType, entityId);
        if (p != null) {
            count(entityType, "projection", "hit");
            if (p.getStatus() != 0) {
                throw notFound(entityType);
            }
            int entityUserId = safeInt(p.getEntityUserId());
            int postId = safeInt(p.getPostId());
            if (entityUserId <= 0 || postId <= 0) {
                count(entityType, "projection", "incomplete");
                throw new BusinessException(SERVICE_UNAVAILABLE, "内容实体投影缺失或不完整");
            }
            return new ResolvedEntity(entityUserId, postId);
        } else {
            count(entityType, "projection", "miss");
        }

        throw new BusinessException(SERVICE_UNAVAILABLE, "内容实体投影缺失或不完整");
    }

    private BusinessException notFound(int entityType) {
        if (entityType == EntityTypes.POST) {
            return new BusinessException(ContentErrorCode.POST_NOT_FOUND);
        }
        if (entityType == EntityTypes.COMMENT) {
            return new BusinessException(ContentErrorCode.COMMENT_NOT_FOUND);
        }
        return new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityType 不支持");
    }

    private int safeInt(long v) {
        if (v <= 0L) {
            return 0;
        }
        if (v > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) v;
    }

    private void count(int entityType, String source, String outcome) {
        meterRegistry.counter(
                "social_entity_resolve_total",
                Tags.of(
                        "entityType", String.valueOf(entityType),
                        "source", String.valueOf(source),
                        "outcome", String.valueOf(outcome)
                )
        ).increment();
    }

    public static final class ResolvedEntity {
        private final int entityUserId;
        private final int postId;

        public ResolvedEntity(int entityUserId, int postId) {
            this.entityUserId = entityUserId;
            this.postId = postId;
        }

        public int getEntityUserId() {
            return entityUserId;
        }

        public int getPostId() {
            return postId;
        }
    }
}
