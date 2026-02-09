package com.nowcoder.community.social.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.internal.dto.EntityResolveResponse;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * content-service internal client：解析 POST/COMMENT 的 owner/postId，避免信任客户端注入字段。
 */
@Service
public class ContentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);
    private static final String SERVICE_NAME = "content-service";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    public ContentServiceClient(
            MeterRegistry meterRegistry,
            @Value("${clients.content.fail-open:false}") boolean failOpen
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
    }

    @DubboReference(check = false, retries = 0, timeout = 1000)
    private com.nowcoder.community.content.api.rpc.ContentEntityRpcService contentEntityRpcService;

    public EntityResolveResponse resolveEntity(int entityType, int entityId) {
        if (entityType <= 0 || entityId <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType/entityId 非法");
        }
        long start = System.nanoTime();
        try {
            Result<EntityResolveResponse> result = contentEntityRpcService.resolveEntity(entityType, entityId);
            EntityResolveResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[content-client] degraded (api=resolveEntity): {}", e.toString());
            } else {
                String outcome = InternalClientSupport.isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "resolveEntity", outcome, start);
            }
            if (e instanceof BusinessException be) {
                throw be;
            }
            if (e instanceof RpcException) {
                throw new BusinessException(SERVICE_UNAVAILABLE, "content-service 不可用");
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "content-service 不可用");
        }
    }
}
