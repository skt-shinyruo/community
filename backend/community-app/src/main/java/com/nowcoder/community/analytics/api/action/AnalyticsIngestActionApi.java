package com.nowcoder.community.analytics.api.action;

import java.util.UUID;

public interface AnalyticsIngestActionApi {

    void recordLoginSuccess(UUID userId);
}
