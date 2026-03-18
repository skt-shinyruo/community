package com.nowcoder.community.social.service;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.internal.api.EntityResolveApi;
import com.nowcoder.community.infra.modulecall.ModuleCallOptions;
import com.nowcoder.community.infra.modulecall.ModuleCallSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * 内容实体元信息解析器（social 写路径可信只读依赖）：
 * - 通过内部接口回源到 content 模块（SSOT）
 * - content 模块不可用时 fail-closed（快速失败）
 */
@Service
public class ContentEntityResolver {

    private static final Logger log = LoggerFactory.getLogger(ContentEntityResolver.class);
    private final MeterRegistry meterRegistry;
    private final EntityResolveApi entityResolveApi;

    public ContentEntityResolver(
            MeterRegistry meterRegistry,
            EntityResolveApi entityResolveApi
    ) {
        this.meterRegistry = meterRegistry;
        this.entityResolveApi = entityResolveApi;
    }

    private static final String TARGET_MODULE = "content";

    public ResolvedEntity resolve(int entityType, int entityId) {
        if (entityId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityType 不支持");
        }

        return resolveInternal(entityType, entityId);
    }

    private ResolvedEntity resolveInternal(int entityType, int entityId) {
        try {
            return ModuleCallSupport.callResultAndThen(
                    meterRegistry,
                    TARGET_MODULE,
                    "resolveEntity",
                    () -> entityResolveApi.resolveEntity(entityType, entityId),
                    data -> {
                        int entityUserId = data == null ? 0 : data.getEntityUserId();
                        int postId = data == null ? 0 : data.getPostId();
                        if (entityUserId <= 0 || postId <= 0) {
                            count(entityType, "internal-api", "incomplete");
                            throw new BusinessException(SERVICE_UNAVAILABLE, "内容实体解析结果缺失或不完整");
                        }
                        count(entityType, "internal-api", "success");
                        return new ResolvedEntity(entityUserId, postId);
                    },
                    ModuleCallOptions.<ResolvedEntity>failClosed().withWarnLogger((m, e) -> log.warn(m, e))
            );
        } catch (RuntimeException e) {
            count(entityType, "internal-api", "error");
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
