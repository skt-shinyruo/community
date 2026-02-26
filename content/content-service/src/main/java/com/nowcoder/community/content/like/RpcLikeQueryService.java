package com.nowcoder.community.content.like;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.social.api.rpc.SocialReadRpcService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.SocketTimeoutException;
import java.util.function.Supplier;

/**
 * 点赞查询（RPC 回源）：直接从 social-service 获取点赞计数/状态。
 *
 * <p>说明：移除本地 Redis 投影后，该查询会同步依赖 social-service；属于“展示类读路径”，因此默认 fail-open。</p>
 */
@Component
public class RpcLikeQueryService implements LikeQueryService {

    private static final Logger log = LoggerFactory.getLogger(RpcLikeQueryService.class);

    private static final String SERVICE_NAME = "social-service";
    private static final int ENTITY_TYPE_POST = EntityTypes.POST;

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    @DubboReference(check = false, retries = 0, timeout = 800)
    private SocialReadRpcService socialReadRpcService;

    public RpcLikeQueryService(
            MeterRegistry meterRegistry,
            @Value("${clients.social.fail-open:true}") boolean failOpen
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
    }

    @Override
    public long countPostLikes(int postId) {
        return call("entityLikeCount", () -> entityLikeCountInternal(postId), () -> 0L);
    }

    @Override
    public boolean hasLikedPost(int userId, int postId) {
        Boolean v = call("hasLiked", () -> hasLikedInternal(userId, postId), () -> Boolean.FALSE);
        return Boolean.TRUE.equals(v);
    }

    private long entityLikeCountInternal(int postId) {
        if (postId <= 0) {
            return 0L;
        }
        Result<Long> result = socialReadRpcService.entityLikeCount(ENTITY_TYPE_POST, postId);
        Long data = InternalClientSupport.unwrap(result, SERVICE_NAME);
        return data == null ? 0L : data;
    }

    private Boolean hasLikedInternal(int userId, int postId) {
        if (userId <= 0 || postId <= 0) {
            return false;
        }
        Result<Boolean> result = socialReadRpcService.hasLiked(userId, ENTITY_TYPE_POST, postId);
        return InternalClientSupport.unwrap(result, SERVICE_NAME);
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (RuntimeException e) {
            String outcome = classifyOutcome(e);
            if (failOpen && fallback != null) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, api, InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-like-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, api, outcome, start);
            throw e;
        }
    }

    private String classifyOutcome(Throwable t) {
        if (isTimeout(t)) {
            return InternalClientSupport.OUTCOME_TIMEOUT;
        }
        return InternalClientSupport.OUTCOME_ERROR;
    }

    private boolean isTimeout(Throwable t) {
        if (t instanceof RpcException re) {
            return re.isTimeout();
        }
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            if (cur instanceof RpcException re) {
                return re.isTimeout();
            }
            cur = cur.getCause();
        }
        return false;
    }
}

