package com.nowcoder.community.content.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.social.api.rpc.SocialBlockScanRpcService;
import com.nowcoder.community.social.api.rpc.dto.SocialBlockScanResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE;

@Service
public class SocialBlockScanClient {

    private static final Logger log = LoggerFactory.getLogger(SocialBlockScanClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final MeterRegistry meterRegistry;

    @DubboReference(check = false, retries = 0, timeout = 2000)
    private SocialBlockScanRpcService socialBlockScanRpcService;

    public SocialBlockScanClient(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public SocialBlockScanResponse scan(int afterBlockerUserId, int afterBlockedUserId, int limit) {
        long start = System.nanoTime();
        try {
            Result<SocialBlockScanResponse> result = socialBlockScanRpcService.scan(afterBlockerUserId, afterBlockedUserId, limit);
            SocialBlockScanResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanBlocks", InternalClientSupport.OUTCOME_SUCCESS, start);
            return data;
        } catch (RuntimeException e) {
            String outcome = isTimeout(e) ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanBlocks", outcome, start);
            if (e instanceof BusinessException be) {
                throw be;
            }
            log.warn("[social-client] scan blocks failed: {}", e.toString());
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

