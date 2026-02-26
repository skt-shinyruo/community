package com.nowcoder.community.search.service;

// content-service 内部调用客户端：用于 reindex 扫描帖子数据。
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.content.api.rpc.ContentScanRpcService;
import com.nowcoder.community.content.api.rpc.dto.ContentPostScanResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * search-service -> content-service 内部调用（用于重建索引）。
 */
@Service
public class ContentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ContentServiceClient.class);
    private static final String SERVICE_NAME = "content-service";

    private final MeterRegistry meterRegistry;

    @DubboReference(check = false, retries = 0, timeout = 5000)
    private ContentScanRpcService contentScanRpcService;

    public ContentServiceClient(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public ContentPostScanResponse scanPosts(int afterId, int limit) {
        long start = System.nanoTime();
        try {
            int a = Math.max(0, afterId);
            int l = Math.min(1000, Math.max(1, limit));
            Result<ContentPostScanResponse> result = contentScanRpcService.scanPosts(a, l);
            ContentPostScanResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "success", start);
            return data;
        } catch (BusinessException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "error", start);
            throw e;
        } catch (RpcException e) {
            String outcome = e.isTimeout() ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", outcome, start);
            log.warn("[content-client] dubbo call failed: {}", e.toString());
            throw new BusinessException(com.nowcoder.community.contracts.api.CommonErrorCode.SERVICE_UNAVAILABLE, "content-service 不可用");
        } catch (RuntimeException e) {
            InternalClientSupport.record(meterRegistry, SERVICE_NAME, "scanPosts", "error", start);
            log.warn("[content-client] call failed: {}", e.toString());
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "content-service 调用失败");
        }
    }
}
