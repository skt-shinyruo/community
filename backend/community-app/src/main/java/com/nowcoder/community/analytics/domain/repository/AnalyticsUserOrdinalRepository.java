package com.nowcoder.community.analytics.domain.repository;

import java.util.UUID;

public interface AnalyticsUserOrdinalRepository {

    int ordinalOf(UUID userId);
}
