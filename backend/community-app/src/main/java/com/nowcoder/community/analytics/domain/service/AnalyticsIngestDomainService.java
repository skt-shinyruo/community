package com.nowcoder.community.analytics.domain.service;

import com.nowcoder.community.analytics.domain.model.AnalyticsRequestEvent;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsIngestDomainService {

    public boolean shouldRecordUv(AnalyticsRequestEvent event) {
        return event != null && event.recordUv() && hasText(event.ip());
    }

    public boolean shouldRecordDau(AnalyticsRequestEvent event) {
        return event != null && event.recordDau() && event.userId() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
