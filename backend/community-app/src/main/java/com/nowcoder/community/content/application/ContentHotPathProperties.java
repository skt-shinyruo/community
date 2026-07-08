package com.nowcoder.community.content.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "content.hot-path")
public class ContentHotPathProperties {

    private final PrewarmProperties prewarm = new PrewarmProperties();
    private final SingleFlightProperties singleFlight = new SingleFlightProperties();
    private final CacheProperties cache = new CacheProperties();

    public PrewarmProperties getPrewarm() {
        return prewarm;
    }

    public SingleFlightProperties getSingleFlight() {
        return singleFlight;
    }

    public CacheProperties getCache() {
        return cache;
    }

    public static class PrewarmProperties {
        private boolean enabled = true;
        private long delayMs = 60_000L;
        private int pages = 2;
        private int pageSize = 20;
        private int boardLimit = 20;
        private long lockTtlSeconds = 30L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getPages() {
            return pages;
        }

        public void setPages(int pages) {
            this.pages = pages;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getBoardLimit() {
            return boardLimit;
        }

        public void setBoardLimit(int boardLimit) {
            this.boardLimit = boardLimit;
        }

        public Duration lockTtl() {
            return Duration.ofSeconds(Math.max(1L, lockTtlSeconds));
        }

        public long getLockTtlSeconds() {
            return lockTtlSeconds;
        }

        public void setLockTtlSeconds(long lockTtlSeconds) {
            this.lockTtlSeconds = lockTtlSeconds;
        }
    }

    public static class SingleFlightProperties {
        private boolean enabled = true;
        private long ttlMs = 3_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }

        public Duration ttl() {
            return Duration.ofMillis(Math.max(1L, ttlMs));
        }
    }

    public static class CacheProperties {
        private long summaryTtlSeconds = 300L;
        private long detailTtlSeconds = 300L;
        private long commentPageTtlSeconds = 120L;
        private long followPageTtlSeconds = 60L;
        private long ttlJitterSeconds = 60L;

        public long getSummaryTtlSeconds() {
            return summaryTtlSeconds;
        }

        public void setSummaryTtlSeconds(long summaryTtlSeconds) {
            this.summaryTtlSeconds = summaryTtlSeconds;
        }

        public long getDetailTtlSeconds() {
            return detailTtlSeconds;
        }

        public void setDetailTtlSeconds(long detailTtlSeconds) {
            this.detailTtlSeconds = detailTtlSeconds;
        }

        public long getCommentPageTtlSeconds() {
            return commentPageTtlSeconds;
        }

        public void setCommentPageTtlSeconds(long commentPageTtlSeconds) {
            this.commentPageTtlSeconds = commentPageTtlSeconds;
        }

        public long getFollowPageTtlSeconds() {
            return followPageTtlSeconds;
        }

        public void setFollowPageTtlSeconds(long followPageTtlSeconds) {
            this.followPageTtlSeconds = followPageTtlSeconds;
        }

        public long getTtlJitterSeconds() {
            return ttlJitterSeconds;
        }

        public void setTtlJitterSeconds(long ttlJitterSeconds) {
            this.ttlJitterSeconds = ttlJitterSeconds;
        }

        public Duration summaryTtl() {
            return seconds(summaryTtlSeconds);
        }

        public Duration detailTtl() {
            return seconds(detailTtlSeconds);
        }

        public Duration commentPageTtl() {
            return seconds(commentPageTtlSeconds);
        }

        public Duration followPageTtl() {
            return seconds(followPageTtlSeconds);
        }

        public Duration ttlJitter() {
            return ttlJitterSeconds <= 0L ? Duration.ZERO : Duration.ofSeconds(ttlJitterSeconds);
        }

        private static Duration seconds(long value) {
            return Duration.ofSeconds(Math.max(1L, value));
        }
    }
}
