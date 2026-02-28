package com.nowcoder.community.social.service;

import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.internal.dto.EntityResolveResponse;
import com.nowcoder.community.contracts.internal.rpc.EntityResolveRpcService;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * 内容实体元信息解析器（social 写路径可信只读依赖）：
 * - 通过内部接口回源到 content 模块（SSOT）
 * - content-service 不可用时 fail-closed（快速失败）
 */
@Service
public class ContentEntityResolver {

    private final MeterRegistry meterRegistry;
    private final EntityResolveRpcService entityResolveRpcService;

    public ContentEntityResolver(
            MeterRegistry meterRegistry,
            EntityResolveRpcService entityResolveRpcService
    ) {
        this.meterRegistry = meterRegistry;
        this.entityResolveRpcService = entityResolveRpcService;
    }

    private static final String SERVICE_NAME = "content-service";

    public ResolvedEntity resolve(int entityType, int entityId) {
        if (entityId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityId 非法");
        }
        if (entityType != EntityTypes.POST && entityType != EntityTypes.COMMENT) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "entityType 不支持");
        }

        long start = System.nanoTime();
        try {
            Result<EntityResolveResponse> result = entityResolveRpcService.resolveEntity(entityType, entityId);
            EntityResolveResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            int entityUserId = data == null ? 0 : data.getEntityUserId();
            int postId = data == null ? 0 : data.getPostId();
            if (entityUserId <= 0 || postId <= 0) {
                count(entityType, "rpc", "incomplete");
                throw new BusinessException(SERVICE_UNAVAILABLE, "内容实体解析结果缺失或不完整");
            }
            count(entityType, "rpc", "success");
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", InternalClientSupport.OUTCOME_SUCCESS, start);
            return new ResolvedEntity(entityUserId, postId);
        } catch (RuntimeException e) {
            count(entityType, "rpc", "error");
            String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "content-service 不可用");
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
