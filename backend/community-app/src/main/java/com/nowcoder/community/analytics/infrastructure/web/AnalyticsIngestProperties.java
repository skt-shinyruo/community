package com.nowcoder.community.analytics.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "analytics.ingest")
public class AnalyticsIngestProperties {

    private boolean enabled = false;
    private boolean recordUv = true;
    private boolean recordDau = true;
    private List<String> includePaths = new ArrayList<>(List.of("/api/posts/**", "/api/search/**", "/api/messages/**", "/api/notices/**", "/api/im-governance/**"));
    private List<String> excludePaths = new ArrayList<>(List.of("/api/analytics/**", "/api/auth/**", "/api/ops/**", "/actuator/**", "/internal/**", "/files/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRecordUv() {
        return recordUv;
    }

    public void setRecordUv(boolean recordUv) {
        this.recordUv = recordUv;
    }

    public boolean isRecordDau() {
        return recordDau;
    }

    public void setRecordDau(boolean recordDau) {
        this.recordDau = recordDau;
    }

    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths == null ? new ArrayList<>() : new ArrayList<>(includePaths);
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }
}
