package com.nowcoder.community.common.observability.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "community.observability.runtime-logging")
public class RuntimeLoggingProperties {

    private boolean enabled = true;
    private boolean startupSummaryEnabled = true;
    private boolean periodicSummaryEnabled = true;
    private Duration periodicSummaryInterval = Duration.ofSeconds(60);
    private final Jvm jvm = new Jvm();
    private final Executors executors = new Executors();
    private final Datasource datasource = new Datasource();
    private final Http http = new Http();
    private final HttpClient httpClient = new HttpClient();
    private final Redis redis = new Redis();
    private final Kafka kafka = new Kafka();
    private final Sql sql = new Sql();
    private final Oss oss = new Oss();
    private final Jobs jobs = new Jobs();
    private final Cache cache = new Cache();
    private final Security security = new Security();
    private final LoggingSystem loggingSystem = new LoggingSystem();
    private final SystemResources system = new SystemResources();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStartupSummaryEnabled() {
        return startupSummaryEnabled;
    }

    public void setStartupSummaryEnabled(boolean startupSummaryEnabled) {
        this.startupSummaryEnabled = startupSummaryEnabled;
    }

    public boolean isPeriodicSummaryEnabled() {
        return periodicSummaryEnabled;
    }

    public void setPeriodicSummaryEnabled(boolean periodicSummaryEnabled) {
        this.periodicSummaryEnabled = periodicSummaryEnabled;
    }

    public Duration getPeriodicSummaryInterval() {
        return periodicSummaryInterval;
    }

    public void setPeriodicSummaryInterval(Duration periodicSummaryInterval) {
        this.periodicSummaryInterval = periodicSummaryInterval;
    }

    public Jvm getJvm() {
        return jvm;
    }

    public Executors getExecutors() {
        return executors;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public Http getHttp() {
        return http;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public Redis getRedis() {
        return redis;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Sql getSql() {
        return sql;
    }

    public Oss getOss() {
        return oss;
    }

    public Jobs getJobs() {
        return jobs;
    }

    public Cache getCache() {
        return cache;
    }

    public Security getSecurity() {
        return security;
    }

    public LoggingSystem getLoggingSystem() {
        return loggingSystem;
    }

    public SystemResources getSystem() {
        return system;
    }

    public static class Jvm {

        private boolean enabled = true;
        private int memoryThresholdPercent = 85;
        private int directMemoryThresholdPercent = 85;
        private long gcPauseThresholdMs = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMemoryThresholdPercent() {
            return memoryThresholdPercent;
        }

        public void setMemoryThresholdPercent(int memoryThresholdPercent) {
            this.memoryThresholdPercent = memoryThresholdPercent;
        }

        public long getGcPauseThresholdMs() {
            return gcPauseThresholdMs;
        }

        public void setGcPauseThresholdMs(long gcPauseThresholdMs) {
            this.gcPauseThresholdMs = gcPauseThresholdMs;
        }

        public int getDirectMemoryThresholdPercent() {
            return directMemoryThresholdPercent;
        }

        public void setDirectMemoryThresholdPercent(int directMemoryThresholdPercent) {
            this.directMemoryThresholdPercent = directMemoryThresholdPercent;
        }
    }

    public static class Executors {

        private boolean enabled = true;
        private int saturationThresholdPercent = 85;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getSaturationThresholdPercent() {
            return saturationThresholdPercent;
        }

        public void setSaturationThresholdPercent(int saturationThresholdPercent) {
            this.saturationThresholdPercent = saturationThresholdPercent;
        }
    }

    public static class Datasource {

        private boolean enabled = true;
        private int poolPendingThreshold = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPoolPendingThreshold() {
            return poolPendingThreshold;
        }

        public void setPoolPendingThreshold(int poolPendingThreshold) {
            this.poolPendingThreshold = poolPendingThreshold;
        }
    }

    public static class Http {

        private boolean accessLogEnabled = true;
        private long slowRequestThresholdMs = 1000;
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/health", "/actuator/info"));

        public boolean isAccessLogEnabled() {
            return accessLogEnabled;
        }

        public void setAccessLogEnabled(boolean accessLogEnabled) {
            this.accessLogEnabled = accessLogEnabled;
        }

        public long getSlowRequestThresholdMs() {
            return slowRequestThresholdMs;
        }

        public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
            this.slowRequestThresholdMs = slowRequestThresholdMs;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
        }
    }

    public static class HttpClient {

        private boolean enabled = true;
        private long slowRequestThresholdMs = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowRequestThresholdMs() {
            return slowRequestThresholdMs;
        }

        public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
            this.slowRequestThresholdMs = slowRequestThresholdMs;
        }
    }

    public static class Redis {

        private boolean enabled = true;
        private int poolPendingThreshold = 1;
        private long slowCommandThresholdMs = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPoolPendingThreshold() {
            return poolPendingThreshold;
        }

        public void setPoolPendingThreshold(int poolPendingThreshold) {
            this.poolPendingThreshold = poolPendingThreshold;
        }

        public long getSlowCommandThresholdMs() {
            return slowCommandThresholdMs;
        }

        public void setSlowCommandThresholdMs(long slowCommandThresholdMs) {
            this.slowCommandThresholdMs = slowCommandThresholdMs;
        }
    }

    public static class Kafka {

        private boolean enabled = true;
        private long consumerLagThreshold = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getConsumerLagThreshold() {
            return consumerLagThreshold;
        }

        public void setConsumerLagThreshold(long consumerLagThreshold) {
            this.consumerLagThreshold = consumerLagThreshold;
        }
    }

    public static class Sql {

        private boolean enabled = true;
        private long slowQueryThresholdMs = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowQueryThresholdMs() {
            return slowQueryThresholdMs;
        }

        public void setSlowQueryThresholdMs(long slowQueryThresholdMs) {
            this.slowQueryThresholdMs = slowQueryThresholdMs;
        }
    }

    public static class Oss {

        private boolean enabled = true;
        private long slowOperationThresholdMs = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowOperationThresholdMs() {
            return slowOperationThresholdMs;
        }

        public void setSlowOperationThresholdMs(long slowOperationThresholdMs) {
            this.slowOperationThresholdMs = slowOperationThresholdMs;
        }
    }

    public static class Jobs {

        private boolean enabled = true;
        private long slowJobThresholdMs = 30000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowJobThresholdMs() {
            return slowJobThresholdMs;
        }

        public void setSlowJobThresholdMs(long slowJobThresholdMs) {
            this.slowJobThresholdMs = slowJobThresholdMs;
        }
    }

    public static class Cache {

        private boolean enabled = true;
        private int hitRatioThresholdPercent = 80;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getHitRatioThresholdPercent() {
            return hitRatioThresholdPercent;
        }

        public void setHitRatioThresholdPercent(int hitRatioThresholdPercent) {
            this.hitRatioThresholdPercent = hitRatioThresholdPercent;
        }
    }

    public static class Security {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class LoggingSystem {

        private boolean enabled = true;
        private int queuePressureThresholdPercent = 80;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueuePressureThresholdPercent() {
            return queuePressureThresholdPercent;
        }

        public void setQueuePressureThresholdPercent(int queuePressureThresholdPercent) {
            this.queuePressureThresholdPercent = queuePressureThresholdPercent;
        }
    }

    public static class SystemResources {

        private boolean enabled = true;
        private int fdUsageThresholdPercent = 80;
        private int diskUsageThresholdPercent = 90;
        private int cpuLoadThresholdPercent = 85;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFdUsageThresholdPercent() {
            return fdUsageThresholdPercent;
        }

        public void setFdUsageThresholdPercent(int fdUsageThresholdPercent) {
            this.fdUsageThresholdPercent = fdUsageThresholdPercent;
        }

        public int getDiskUsageThresholdPercent() {
            return diskUsageThresholdPercent;
        }

        public void setDiskUsageThresholdPercent(int diskUsageThresholdPercent) {
            this.diskUsageThresholdPercent = diskUsageThresholdPercent;
        }

        public int getCpuLoadThresholdPercent() {
            return cpuLoadThresholdPercent;
        }

        public void setCpuLoadThresholdPercent(int cpuLoadThresholdPercent) {
            this.cpuLoadThresholdPercent = cpuLoadThresholdPercent;
        }
    }
}
