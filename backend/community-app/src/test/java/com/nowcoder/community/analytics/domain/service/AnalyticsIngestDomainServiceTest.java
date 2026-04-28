package com.nowcoder.community.analytics.domain.service;

import com.nowcoder.community.analytics.domain.model.AnalyticsRequestEvent;
import org.junit.jupiter.api.Test;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsIngestDomainServiceTest {

    private final AnalyticsIngestDomainService service = new AnalyticsIngestDomainService();

    @Test
    void shouldRecordUvOnlyWhenEnabledAndIpPresent() {
        assertThat(service.shouldRecordUv(new AnalyticsRequestEvent("1.1.1.1", null, true, false))).isTrue();
        assertThat(service.shouldRecordUv(new AnalyticsRequestEvent(" ", null, true, false))).isFalse();
        assertThat(service.shouldRecordUv(new AnalyticsRequestEvent("1.1.1.1", null, false, false))).isFalse();
    }

    @Test
    void shouldRecordDauOnlyWhenEnabledAndUserPresent() {
        assertThat(service.shouldRecordDau(new AnalyticsRequestEvent(null, uuid(1), false, true))).isTrue();
        assertThat(service.shouldRecordDau(new AnalyticsRequestEvent(null, null, false, true))).isFalse();
        assertThat(service.shouldRecordDau(new AnalyticsRequestEvent(null, uuid(1), false, false))).isFalse();
    }
}
