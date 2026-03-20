package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.config.UserSocialProfileProperties;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import com.nowcoder.community.common.constants.EntityTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class UserSocialProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserSocialProfileService.class);
    private static final String INTERNAL_CALL_REQUESTS_TOTAL = "internal_call_requests_total";
    private static final String INTERNAL_CALL_LATENCY = "internal_call_latency";
    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_ERROR = "error";
    private static final String OUTCOME_TIMEOUT = "timeout";
    private static final String OUTCOME_UNAVAILABLE = "unavailable";
    private static final String OUTCOME_DEGRADED = "degraded";
    private static final String OUTCOME_FORBIDDEN = "forbidden";
    private static final String OUTCOME_REMOTE_ERROR = "remote_error";
    private static final String TARGET_MODULE = "social";
    private static final int USER_ENTITY_TYPE = EntityTypes.USER;

    private final MeterRegistry meterRegistry;
    private final UserSocialProfileProperties properties;
    private final LikeService likeService;
    private final FollowService followService;

    public UserSocialProfileService(
            MeterRegistry meterRegistry,
            UserSocialProfileProperties properties,
            LikeService likeService,
            FollowService followService
    ) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.likeService = likeService;
        this.followService = followService;
    }

    public UserProfileStats safeUserProfileStats(int userId, int viewerId) {
        if (userId <= 0) {
            return UserProfileStats.empty();
        }
        return call("profileStats", () -> userProfileStatsInternal(userId, viewerId), UserProfileStats::degradedFallback);
    }

    public long safeUserLikeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return call("userLikeCount", () -> userLikeCountInternal(userId), () -> 0L);
    }

    public long safeFolloweeCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return call("followeeCount", () -> followeeCountInternal(userId), () -> 0L);
    }

    public long safeFollowerCount(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return call("followerCount", () -> followerCountInternal(userId), () -> 0L);
    }

    public boolean safeHasFollowed(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        Boolean value = call("hasFollowed", () -> hasFollowedInternal(actorUserId, targetUserId), () -> Boolean.FALSE);
        return Boolean.TRUE.equals(value);
    }

    private UserProfileStats userProfileStatsInternal(int userId, int viewerId) {
        if (userId <= 0) {
            return UserProfileStats.empty();
        }

        UserProfileStats response = new UserProfileStats();
        response.setLikeCount(likeService.userLikeCount(userId));
        response.setFolloweeCount(followService.followeeCount(userId, USER_ENTITY_TYPE));
        response.setFollowerCount(followService.followerCount(USER_ENTITY_TYPE, userId));

        if (viewerId > 0 && viewerId != userId) {
            response.setHasFollowed(followService.hasFollowed(viewerId, USER_ENTITY_TYPE, userId));
        }
        return response;
    }

    private long userLikeCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return likeService.userLikeCount(userId);
    }

    private long followeeCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followService.followeeCount(userId, USER_ENTITY_TYPE);
    }

    private long followerCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return followService.followerCount(USER_ENTITY_TYPE, userId);
    }

    private Boolean hasFollowedInternal(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        return followService.hasFollowed(actorUserId, USER_ENTITY_TYPE, targetUserId);
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T value = supplier.get();
            record(api, OUTCOME_SUCCESS, start);
            return value;
        } catch (RuntimeException e) {
            if (fallback != null && properties.isDegradeOnError()) {
                record(api, OUTCOME_DEGRADED, start);
                log.warn("[internal-call] module={} api={} outcome={}", TARGET_MODULE, api, OUTCOME_DEGRADED, e);
                return fallback.get();
            }
            if (e instanceof BusinessException be) {
                record(api, classifyBusinessOutcome(be), start);
                throw be;
            }
            String outcome = classifyUnexpectedOutcome(e);
            record(api, outcome, start);
            log.warn("[internal-call] module={} api={} outcome={}", TARGET_MODULE, api, outcome, e);
            throw wrapUnexpectedException(e);
        }
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("module", TARGET_MODULE, "api", String.valueOf(api), "outcome", String.valueOf(outcome));
        meterRegistry.counter(INTERNAL_CALL_REQUESTS_TOTAL, tags).increment();
        meterRegistry.timer(INTERNAL_CALL_LATENCY, tags).record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private String classifyBusinessOutcome(BusinessException e) {
        if (e == null || e.getErrorCode() == null) {
            return OUTCOME_REMOTE_ERROR;
        }
        int httpStatus = e.getErrorCode().getHttpStatus();
        if (httpStatus == CommonErrorCode.FORBIDDEN.getHttpStatus()) {
            return OUTCOME_FORBIDDEN;
        }
        if (httpStatus == CommonErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()) {
            return OUTCOME_UNAVAILABLE;
        }
        if (httpStatus == 504) {
            return OUTCOME_TIMEOUT;
        }
        return OUTCOME_REMOTE_ERROR;
    }

    private String classifyUnexpectedOutcome(Throwable t) {
        if (isTimeout(t)) {
            return OUTCOME_TIMEOUT;
        }
        if (isConnectionError(t)) {
            return OUTCOME_UNAVAILABLE;
        }
        return OUTCOME_ERROR;
    }

    private BusinessException wrapUnexpectedException(Throwable t) {
        if (isTimeout(t) || isConnectionError(t)) {
            return new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, TARGET_MODULE + " 不可用", t);
        }
        return new BusinessException(CommonErrorCode.INTERNAL_ERROR, TARGET_MODULE + " 调用失败", t);
    }

    private boolean isTimeout(Throwable t) {
        if (t == null) {
            return false;
        }
        if (hasCause(t, SocketTimeoutException.class) || hasCause(t, TimeoutException.class)) {
            return true;
        }
        if (hasCauseByName(t, "java.net.http.HttpTimeoutException")) {
            return true;
        }
        String message = String.valueOf(t.getMessage());
        return containsIgnoreCase(message, "timed out") || containsIgnoreCase(message, "timeout");
    }

    private boolean isConnectionError(Throwable t) {
        if (t == null) {
            return false;
        }
        return hasCause(t, ConnectException.class)
                || hasCause(t, UnknownHostException.class)
                || hasCause(t, NoRouteToHostException.class)
                || hasCause(t, SocketException.class);
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(needle)) {
            return false;
        }
        return value.toLowerCase().contains(needle.toLowerCase());
    }

    private boolean hasCauseByName(Throwable t, String className) {
        if (!StringUtils.hasText(className)) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (className.equals(cur.getClass().getName())) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        if (t == null || type == null) {
            return false;
        }
        Throwable cur = t;
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        while (cur != null && !seen.containsKey(cur)) {
            seen.put(cur, Boolean.TRUE);
            if (type.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
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
            UserProfileStats value = new UserProfileStats();
            value.setDegraded(true);
            return value;
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
