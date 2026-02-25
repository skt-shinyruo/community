package com.nowcoder.community.content.like;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.social.api.rpc.SocialLikeScanRpcService;
import com.nowcoder.community.social.api.rpc.dto.SocialLikeScanResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

/**
 * social-service internal likes 扫描客户端：用于回填 Redis 点赞投影（减少冷启动窗口）。
 */
@Service
public class SocialLikeScanClient {

    private static final Logger log = LoggerFactory.getLogger(SocialLikeScanClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    public SocialLikeScanClient(
            MeterRegistry meterRegistry,
            @Value("${clients.social.fail-open:false}") boolean failOpen
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
    }

    @DubboReference(check = false, retries = 0, timeout = 5000)
    private SocialLikeScanRpcService socialLikeScanRpcService;

    public SocialLikeScanResponse scan(int entityType, long afterEntityId, long afterUserId, int limit) {
        if (entityType <= 0) {
            throw new BusinessException(INVALID_ARGUMENT, "entityType 非法");
        }
        long ae = Math.max(0L, afterEntityId);
        long au = Math.max(0L, afterUserId);
        int l = Math.min(2000, Math.max(1, limit));
        long start = System.nanoTime();
        try {
            Result<SocialLikeScanResponse> result = socialLikeScanRpcService.scan(entityType, ae, au, l);
            SocialLikeScanResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data == null ? new SocialLikeScanResponse() : data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-like-scan] degraded: {}", e.toString());
                return new SocialLikeScanResponse();
            }
            String outcome = isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanLikes", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            if (e instanceof RpcException) {
                throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
        }
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof RpcException re) {
            return re.isTimeout();
        }
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof RpcException re) {
                return re.isTimeout();
            }
            cur = cur.getCause();
        }
        return false;
    }
}
