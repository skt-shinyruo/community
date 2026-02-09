package com.nowcoder.community.content.service;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.web.internalclient.InternalClientSupport;
import com.nowcoder.community.social.api.rpc.SocialBlockRpcService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;

import static com.nowcoder.community.common.api.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.api.CommonErrorCode.SERVICE_UNAVAILABLE;

// social-service 内部拉黑关系查询客户端：用于写路径校验（评论/私信等）。

@Service
public class SocialBlockClient {

    private static final Logger log = LoggerFactory.getLogger(SocialBlockClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final MeterRegistry meterRegistry;
    private final boolean failOpen;

    public SocialBlockClient(
            MeterRegistry meterRegistry,
            @Value("${clients.social.fail-open:false}") boolean failOpen
    ) {
        this.meterRegistry = meterRegistry;
        this.failOpen = failOpen;
    }

    @DubboReference(check = false, retries = 0, timeout = 800)
    private SocialBlockRpcService socialBlockRpcService;

    public void assertNotBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
            return;
        }
        Boolean blocked = isEitherBlocked(userIdA, userIdB);
        if (Boolean.TRUE.equals(blocked)) {
            throw new BusinessException(FORBIDDEN, "双方存在拉黑关系，无法执行该操作");
        }
    }

    public Boolean isEitherBlocked(int userIdA, int userIdB) {
        if (userIdA <= 0 || userIdB <= 0 || userIdA == userIdB) {
            return false;
        }
        long start = System.nanoTime();
        try {
            Result<Boolean> result = socialBlockRpcService.isEitherBlocked(userIdA, userIdB);
            Boolean data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            if (failOpen) {
                InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-client] degraded (api=isEitherBlocked): {}", e.toString());
                return false;
            }
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", classifyOutcome(e), start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException(SERVICE_UNAVAILABLE, "social-service 不可用");
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
