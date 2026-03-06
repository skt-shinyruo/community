package com.nowcoder.community.user.service;

import com.nowcoder.community.social.application.SocialReadApplicationService;
import com.nowcoder.community.social.application.dto.SocialUserProfileStats;
import com.nowcoder.community.user.config.UserSocialProfileProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class UserSocialProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserSocialProfileService.class);

    private final MeterRegistry meterRegistry;
    private final UserSocialProfileProperties properties;
    private final SocialReadApplicationService socialReadApplicationService;

    public UserSocialProfileService(
            MeterRegistry meterRegistry,
            UserSocialProfileProperties properties,
            SocialReadApplicationService socialReadApplicationService
    ) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.socialReadApplicationService = socialReadApplicationService;
    }

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
        Boolean value = call("hasFollowed", () -> hasFollowedInternal(actorUserId, targetUserId), () -> Boolean.FALSE);
        return Boolean.TRUE.equals(value);
    }

    private UserProfileStats userProfileStatsInternal(int userId, int viewerId) {
        if (userId <= 0) {
            return UserProfileStats.empty();
        }

        Integer viewer = viewerId > 0 && viewerId != userId ? viewerId : null;
        SocialUserProfileStats data = socialReadApplicationService.userProfileStats(userId, viewer);
        if (data == null) {
            return UserProfileStats.empty();
        }

        UserProfileStats response = new UserProfileStats();
        response.setLikeCount(data.getLikeCount());
        response.setFolloweeCount(data.getFolloweeCount());
        response.setFollowerCount(data.getFollowerCount());
        response.setHasFollowed(data.isHasFollowed());
        return response;
    }

    private long userLikeCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return socialReadApplicationService.userLikeCount(userId);
    }

    private long followeeCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return socialReadApplicationService.followeeCount(userId);
    }

    private long followerCountInternal(int userId) {
        if (userId <= 0) {
            return 0L;
        }
        return socialReadApplicationService.followerCount(userId);
    }

    private Boolean hasFollowedInternal(int actorUserId, int targetUserId) {
        if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
            return false;
        }
        return socialReadApplicationService.hasFollowedUser(actorUserId, targetUserId);
    }

    private <T> T call(String api, Supplier<T> supplier, Supplier<T> fallback) {
        long start = System.nanoTime();
        try {
            T value = supplier.get();
            record(api, "success", start);
            return value;
        } catch (RuntimeException e) {
            if (fallback != null && properties.isDegradeOnError()) {
                record(api, "degraded", start);
                log.warn("[user-social-profile] degraded (api={}): {}", api, e.toString());
                return fallback.get();
            }
            record(api, "error", start);
            throw e;
        }
    }

    private void record(String api, String outcome, long startNanos) {
        if (meterRegistry == null) {
            return;
        }
        Tags tags = Tags.of("api", api, "outcome", outcome);
        meterRegistry.counter("user_social_profile_requests_total", tags).increment();
        meterRegistry.timer("user_social_profile_latency", tags)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
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
