package com.nowcoder.community.im.realtime.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 调用 community-app 的 IM 治理校验接口（转发用户 JWT）。
 *
 * <p>注意：该客户端只依赖 JSON 形状，不直接依赖 community 的 contracts 包，避免模块耦合。</p>
 */
@Component
public class CommunityGovernanceClient {

    private final BaseUrlPool baseUrlPool;
    private final Duration timeout;

    @Autowired
    public CommunityGovernanceClient(
            Environment environment,
            @Value("${im.community.base-url}") String baseUrl,
            @Value("${im.community.timeout-ms:1500}") long timeoutMs
    ) {
        this(
                BaseUrlPool.from(
                        "im.community",
                        baseUrl,
                        environment == null
                                ? List.of()
                                : Binder.get(environment)
                                        .bind("im.community.base-urls", Bindable.listOf(String.class))
                                        .orElse(List.of())
                ),
                timeoutMs
        );
    }

    public CommunityGovernanceClient(String baseUrl, long timeoutMs) {
        this(BaseUrlPool.from("im.community", baseUrl, List.of()), timeoutMs);
    }

    public CommunityGovernanceClient(List<String> baseUrls, long timeoutMs) {
        this(BaseUrlPool.from("im.community", baseUrls), timeoutMs);
    }

    private CommunityGovernanceClient(BaseUrlPool baseUrlPool, long timeoutMs) {
        this.baseUrlPool = baseUrlPool;
        long ms = Math.max(100L, timeoutMs);
        this.timeout = Duration.ofMillis(ms);
    }

    public Mono<Decision> validateSendPrivateMessage(String bearerAccessToken, int toUserId, String traceId) {
        String token = StringUtils.hasText(bearerAccessToken) ? bearerAccessToken.trim() : "";
        if (!StringUtils.hasText(token)) {
            return Mono.just(Decision.deny(401, "未登录或登录已失效", ""));
        }
        return validateAgainstCandidates(token, toUserId, traceId, baseUrlPool.nextCandidates(), 0);
    }

    private Mono<Decision> validateAgainstCandidates(
            String token,
            int toUserId,
            String traceId,
            List<String> candidates,
            int index
    ) {
        if (candidates == null || index >= candidates.size()) {
            return Mono.just(Decision.deny(503, "治理校验服务不可用，请稍后重试", ""));
        }

        WebClient webClient = WebClient.builder()
                .baseUrl(candidates.get(index))
                .build();
        RequestHeadersSpec<?> request = webClient.post()
                .uri("/api/im-governance/private-messages/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(new ValidatePrivateMessageRequest(toUserId));

        applyTraceHeaders(request, traceId);

        return request.exchangeToMono(this::decodeDecision)
                .timeout(timeout)
                .onErrorResume(ex -> validateAgainstCandidates(token, toUserId, traceId, candidates, index + 1));
    }

    private static void applyTraceHeaders(RequestHeadersSpec<?> request, String traceId) {
        String normalizedTraceId = TraceIdCodec.normalizeTraceId(traceId);
        if (normalizedTraceId == null) {
            return;
        }
        request.header(TraceHeaders.HEADER_TRACE_ID, normalizedTraceId);
        request.header(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(normalizedTraceId));
    }

    private Mono<Decision> decodeDecision(ClientResponse resp) {
        if (resp == null) {
            return Mono.just(Decision.deny(503, "治理校验服务不可用，请稍后重试", ""));
        }

        int httpStatus = resp.statusCode().value();
        String headerTraceId = firstHeader(resp, "X-Trace-Id");

        return resp.bodyToMono(ResultEnvelope.class)
                .onErrorResume(ex -> Mono.empty())
                .defaultIfEmpty(new ResultEnvelope<>())
                .map(body -> {
                    Integer code = body == null ? null : body.code;
                    String message = body == null ? "" : body.message;
                    String traceId = StringUtils.hasText(body == null ? null : body.traceId) ? body.traceId : headerTraceId;

                    if (code != null && code == 0) {
                        return Decision.allow(traceId);
                    }
                    if (code == null) {
                        int derived = httpStatus >= 400 && httpStatus < 600 ? httpStatus : 503;
                        String msg = StringUtils.hasText(message) ? message : defaultMessageForHttpStatus(derived);
                        return Decision.deny(derived, msg, traceId);
                    }

                    String msg = StringUtils.hasText(message) ? message : defaultMessageForHttpStatus(httpStatus);
                    return Decision.deny(code, msg, traceId);
                });
    }

    private static String firstHeader(ClientResponse resp, String name) {
        try {
            if (resp == null || !StringUtils.hasText(name)) {
                return "";
            }
            List<String> values = resp.headers().header(name);
            if (values == null || values.isEmpty()) {
                return "";
            }
            String v = values.get(0);
            return StringUtils.hasText(v) ? v.trim() : "";
        } catch (RuntimeException ignore) {
            return "";
        }
    }

    private static String defaultMessageForHttpStatus(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> "参数错误";
            case 401 -> "未登录或登录已失效";
            case 403 -> "无权限访问";
            case 404 -> "资源不存在";
            case 429 -> "请求过于频繁";
            case 503 -> "服务不可用";
            default -> "服务端异常";
        };
    }

    public record ValidatePrivateMessageRequest(int toUserId) {
    }

    public record Decision(boolean allowed, int code, String message, String traceId) {

        public static Decision allow(String traceId) {
            return new Decision(true, 0, "OK", traceId == null ? "" : traceId);
        }

        public static Decision deny(int code, String message, String traceId) {
            return new Decision(false, code, String.valueOf(message), traceId == null ? "" : traceId);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultEnvelope<T> {
        public Integer code;
        public String message;
        public Integer httpStatus;
        public T data;
        public String traceId;
        public Long timestamp;
    }
}
