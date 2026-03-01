package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.social.api.rpc.SocialBlockRpcService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;

import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

@Service
public class SocialBlockClient {

    private static final String SERVICE_NAME = "social-service";

    private final MeterRegistry meterRegistry;
    private final SocialBlockRpcService socialBlockRpcService;

    public SocialBlockClient(MeterRegistry meterRegistry, SocialBlockRpcService socialBlockRpcService) {
        this.meterRegistry = meterRegistry;
        this.socialBlockRpcService = socialBlockRpcService;
    }

    public boolean isEitherBlocked(int userIdA, int userIdB) {
        int a = Math.max(0, userIdA);
        int b = Math.max(0, userIdB);
        if (a <= 0 || b <= 0 || a == b) {
            return false;
        }

        long start = System.nanoTime();
        try {
            Result<Boolean> result = socialBlockRpcService.isEitherBlocked(a, b);
            Boolean data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "isEitherBlocked", InternalClientSupport.OUTCOME_SUCCESS, start);
            return Boolean.TRUE.equals(data);
        } catch (RuntimeException e) {
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
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
