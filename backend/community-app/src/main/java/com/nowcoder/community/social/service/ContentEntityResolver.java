package com.nowcoder.community.social.service;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.content.api.model.ResolvedContentRef;
import com.nowcoder.community.content.api.query.ContentEntityQueryApi;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * 内容实体元信息解析器（social 写路径可信只读依赖）：
 * - 通过内部接口回源到 content 模块（SSOT）
 * - content 模块不可用时 fail-closed（快速失败）
 */
@Service
public class ContentEntityResolver {

    private static final Logger log = LoggerFactory.getLogger(ContentEntityResolver.class);
    private final MeterRegistry meterRegistry;
    private final ContentEntityQueryApi contentEntityQueryApi;

    public ContentEntityResolver(
            MeterRegistry meterRegistry,
            ContentEntityQueryApi contentEntityQueryApi
    ) {
        this.meterRegistry = meterRegistry;
        this.contentEntityQueryApi = contentEntityQueryApi;
    }

    public ResolvedEntity resolve(int entityType, UUID entityId) {
        if (entityId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityType 不支持");
        }

        return resolveInternal(entityType, entityId);
    }

    private ResolvedEntity resolveInternal(int entityType, UUID entityId) {
        try {
            ResolvedContentRef data = contentEntityQueryApi.resolve(entityType, entityId);
            UUID entityUserId = data == null ? null : data.entityUserId();
            UUID postId = data == null ? null : data.postId();
            if (entityUserId == null || postId == null) {
                count(entityType, "service", "incomplete");
                throw new BusinessException(SERVICE_UNAVAILABLE, "内容实体解析结果缺失或不完整");
            }
            count(entityType, "service", "success");
            return new ResolvedEntity(entityUserId, postId);
        } catch (RuntimeException e) {
            count(entityType, "service", "error");
            log.warn("[content-entity-resolve] entityType={} entityId={} failed", entityType, entityId, e);
            throw e;
        }
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
        private final UUID entityUserId;
        private final UUID postId;

        public ResolvedEntity(UUID entityUserId, UUID postId) {
            this.entityUserId = entityUserId;
            this.postId = postId;
        }

        public UUID getEntityUserId() {
            return entityUserId;
        }

        public UUID getPostId() {
            return postId;
        }
    }
}
