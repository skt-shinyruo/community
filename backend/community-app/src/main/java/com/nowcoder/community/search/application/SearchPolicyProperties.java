package com.nowcoder.community.search.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchPolicyProperties {

    private String storage = "es";
    private boolean projectionEnabled = true;
    private final Index index = new Index();
    private final Query query = new Query();
    private final Idempotency idempotency = new Idempotency();
    private final Degradation degradation = new Degradation();

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }

    public Index getIndex() {
        return index;
    }

    public Query getQuery() {
        return query;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public Degradation getDegradation() {
        return degradation;
    }

    public static class Index {

        private String prefix = "community_posts_v";
        private boolean initialize = true;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public boolean isInitialize() {
            return initialize;
        }

        public void setInitialize(boolean initialize) {
            this.initialize = initialize;
        }
    }

    public static class Query {

        private String defaultSort = "relevance";
        private int maxPageSize = 50;
        private int maxResultWindow = 10000;
        private int timeoutMs = 1500;

        public String getDefaultSort() {
            return defaultSort;
        }

        public void setDefaultSort(String defaultSort) {
            this.defaultSort = defaultSort;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = Math.max(1, maxPageSize);
        }

        public int getMaxResultWindow() {
            return maxResultWindow;
        }

        public void setMaxResultWindow(int maxResultWindow) {
            this.maxResultWindow = Math.max(1, maxResultWindow);
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = Math.max(1, timeoutMs);
        }
    }

    public static class Idempotency {

        private boolean cleanupEnabled = true;
        private int retentionDays = 7;
        private long cleanupIntervalMs = 21600000L;

        public boolean isCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = Math.max(0, retentionDays);
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public void setCleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = Math.max(1L, cleanupIntervalMs);
        }
    }

    public static class Degradation {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
