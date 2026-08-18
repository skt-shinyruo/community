package com.nowcoder.community.search.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchPolicyProperties {

    private boolean projectionEnabled = true;
    private final Query query = new Query();
    private final Degradation degradation = new Degradation();

    public boolean isProjectionEnabled() {
        return projectionEnabled;
    }

    public void setProjectionEnabled(boolean projectionEnabled) {
        this.projectionEnabled = projectionEnabled;
    }

    public Query getQuery() {
        return query;
    }

    public Degradation getDegradation() {
        return degradation;
    }

    public static class Query {

        private int maxPageSize = 50;

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = Math.max(1, maxPageSize);
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
