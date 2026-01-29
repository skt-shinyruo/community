package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public class GatewayRateLimitProperties {

    private boolean enabled = true;
    private boolean failOpen = true;
    /**
     * 运维/高风险入口关闭开关：匹配到的路径直接按 404 拒绝（降低攻击面）。
     * <p>说明：这是“强关闭”，与限流规则 enabled=false 不同；常用于临时下线 reindex / replay 等高成本操作。</p>
     */
    private List<String> blockedPathPatterns = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public List<String> getBlockedPathPatterns() {
        return blockedPathPatterns;
    }

    public void setBlockedPathPatterns(List<String> blockedPathPatterns) {
        this.blockedPathPatterns = blockedPathPatterns == null ? new ArrayList<>() : blockedPathPatterns;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    public static class Rule {
        private String id;
        private boolean enabled = true;
        private List<String> methods = new ArrayList<>();
        private List<String> pathPatterns = new ArrayList<>();
        private int windowSeconds = 60;
        private int maxRequests = 30;
        private KeyStrategy keyStrategy = KeyStrategy.IP;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods == null ? new ArrayList<>() : methods;
        }

        public List<String> getPathPatterns() {
            return pathPatterns;
        }

        public void setPathPatterns(List<String> pathPatterns) {
            this.pathPatterns = pathPatterns == null ? new ArrayList<>() : pathPatterns;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }

        public KeyStrategy getKeyStrategy() {
            return keyStrategy;
        }

        public void setKeyStrategy(KeyStrategy keyStrategy) {
            this.keyStrategy = keyStrategy == null ? KeyStrategy.IP : keyStrategy;
        }
    }

    public enum KeyStrategy {
        IP,
        USER,
        USER_OR_IP
    }
}
