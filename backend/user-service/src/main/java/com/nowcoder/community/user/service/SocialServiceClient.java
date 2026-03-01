package com.nowcoder.community.user.service;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.user.config.SocialServiceClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * user-service -> social-service 聚合展示客户端：
 * - 仅用于用户主页等读路径的“计数/状态”聚合；
 * - 调用 social-service internal read API，避免跨服务透传 Authorization（降低鉴权耦合）。
 */
@Service
public class SocialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SocialServiceClient.class);
    private static final String SERVICE_NAME = "social-service";

    private final MeterRegistry meterRegistry;
    private final SocialServiceClientProperties properties;
    private final com.nowcoder.community.social.api.rpc.SocialReadRpcService socialReadRpcService;

    public SocialServiceClient(
            MeterRegistry meterRegistry,
            SocialServiceClientProperties properties,
            com.nowcoder.community.social.api.rpc.SocialReadRpcService socialReadRpcService
    ) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.socialReadRpcService = socialReadRpcService;
    }

    /**
     * 用户主页聚合：一次性获取获赞/关注/粉丝/关注状态。
     *
     * <p>注意：该方法用于“展示类读路径”，因此允许按配置 fail-open（降级为 0/false，并标记 degraded）。</p>
     */
    public UserProfileStats safeUserProfileStats(int userId, int viewerId) {
        return call("profileStats", () -> userProfileStatsInternal(userId, viewerId), UserProfileStats::degradedFallback);
    }

    public long safeUserLikeCount(int userId) {
        return call("userLikeCount", () -> userLikeCountInternal(userId), () -> 0L);
    }

    public long safeFolloweeCount(int userId) {
        return call("followeeCount", () -> followeeCountInternal(userId), () -> 0L);
    }

    public long safeFollowerCount(int userId) {
        return call("followerCount", () -> followerCountInternal(userId), () -> 0L);
    }

    public boolean safeHasFollowed(int actorUserId, int targetUserId) {
        Boolean v = call("hasFollowed", () -> hasFollowedInternal(actorUserId, targetUserId), () -> Boolean.FALSE);
        return Boolean.TRUE.equals(v);
    }

    public long userLikeCount(int userId) {
        return call("userLikeCount", () -> userLikeCountInternal(userId), null);
    }

    public long followeeCount(int userId) {
        return call("followeeCount", () -> followeeCountInternal(userId), null);
    }

    public long followerCount(int userId) {
        return call("followerCount", () -> followerCountInternal(userId), null);
    }

    public Boolean hasFollowed(int actorUserId, int targetUserId) {
        return call("hasFollowed", () -> hasFollowedInternal(actorUserId, targetUserId), null);
    }

    private UserProfileStats userProfileStatsInternal(int userId, int viewerId) {
        if (userId <= 0) {
            return UserProfileStats.empty();
        }

        Integer v = viewerId > 0 && viewerId != userId ? viewerId : null;
        Result<com.nowcoder.community.social.api.rpc.dto.UserProfileStats> result = socialReadRpcService.userProfileStats(userId, v);
        com.nowcoder.community.social.api.rpc.dto.UserProfileStats data = InternalClientSupport.unwrap(result, SERVICE_NAME);
        if (data == null) {
            return UserProfileStats.empty();
        }
        UserProfileStats resp = new UserProfileStats();
        resp.setLikeCount(data.getLikeCount());
        resp.setFolloweeCount(data.getFolloweeCount());
        resp.setFollowerCount(data.getFollowerCount());
        resp.setHasFollowed(data.isHasFollowed());
        return resp;
    }

    private long userLikeCountInternal(int userId) {
        if (userId <= 0) {
            return 0;
        }
        Result<Long> result = socialReadRpcService.userLikeCount(userId);
        Long data = InternalClientSupport.unwrap(result, SERVICE_NAME);
        return data == null ? 0 : data;
    }

    private long followeeCountInternal(int userId) {
        if (userId <= 0) {
            return 0;
        }
        Result<Long> result = socialReadRpcService.followeeCount(userId);
        Long data = InternalClientSupport.unwrap(result, SERVICE_NAME);
        return data == null ? 0 : data;
    }

    private long followerCountInternal(int userId) {
        if (userId <= 0) {
            return 0;
        }
        Result<Long> result = socialReadRpcService.followerCount(userId);
        Long data = InternalClientSupport.unwrap(result, SERVICE_NAME);
        return data == null ? 0 : data;
    }

    private Boolean hasFollowedInternal(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        Result<Boolean> result = socialReadRpcService.hasFollowedUser(actorUserId, targetUserId);
        return InternalClientSupport.unwrap(result, SERVICE_NAME);
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            record(api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (RuntimeException e) {
            String outcome = classifyOutcome(e);
            if (fallback != null && properties.isFailOpen()) {
                record(api, InternalClientSupport.OUTCOME_DEGRADED, start);
                log.warn("[social-client] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            record(api, outcome, start);
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
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("user_social_client_requests_total", tags).increment();
        meterRegistry.timer("user_social_client_latency", tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    public static class UserProfileStats {

        private long likeCount;
        private long followeeCount;
        private long followerCount;
        private boolean hasFollowed;
        private boolean degraded;

        public static UserProfileStats empty() {
            return new UserProfileStats();
        }

        public static UserProfileStats degradedFallback() {
            UserProfileStats v = new UserProfileStats();
            v.setDegraded(true);
            return v;
        }

        public long getLikeCount() {
            return likeCount;
        }

        public void setLikeCount(long likeCount) {
            this.likeCount = likeCount;
        }

        public long getFolloweeCount() {
            return followeeCount;
        }

        public void setFolloweeCount(long followeeCount) {
            this.followeeCount = followeeCount;
        }

        public long getFollowerCount() {
            return followerCount;
        }

        public void setFollowerCount(long followerCount) {
            this.followerCount = followerCount;
        }

        public boolean isHasFollowed() {
            return hasFollowed;
        }

        public void setHasFollowed(boolean hasFollowed) {
            this.hasFollowed = hasFollowed;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public void setDegraded(boolean degraded) {
            this.degraded = degraded;
        }
    }
}
