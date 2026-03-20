package com.nowcoder.community.infra.web;

import com.nowcoder.community.infra.trace.TraceId;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 同步 HTTP 调用（RestTemplate）traceId 透传：
 * - 读取当前线程的 TraceId（由 TraceIdFilter 注入）
 * - 写入下游请求头 `X-Trace-Id`
 */
public class TraceIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (request != null && request.getHeaders() != null && !request.getHeaders().containsKey(TraceHeaders.HEADER_TRACE_ID)) {
            String traceId = TraceId.get();
            if (StringUtils.hasText(traceId)) {
                request.getHeaders().set(TraceHeaders.HEADER_TRACE_ID, traceId);
            }
        }
        return execution.execute(request, body);
    }
}
