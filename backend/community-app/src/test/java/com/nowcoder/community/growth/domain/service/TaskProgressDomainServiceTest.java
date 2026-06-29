package com.nowcoder.community.growth.domain.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskProgressDomainServiceTest {

    private final TaskProgressDomainService service = new TaskProgressDomainService();

    @Test
    void periodKeyShouldResolveDailyWeeklyAndLifetimeRules() {
        LocalDate bizDate = LocalDate.of(2026, 3, 16);

        assertThat(service.periodKey(null, bizDate)).isEqualTo("2026-03-16");
        assertThat(service.periodKey(" ", bizDate)).isEqualTo("2026-03-16");
        assertThat(service.periodKey("DAILY", bizDate)).isEqualTo("2026-03-16");
        assertThat(service.periodKey("WEEKLY", bizDate)).isEqualTo("2026-W12");
        assertThat(service.periodKey("LIFETIME", bizDate)).isEqualTo("LIFETIME");
    }

    @Test
    void unsupportedPeriodTypeShouldBeRejected() {
        assertThatThrownBy(() -> service.periodKey("MONTHLY", LocalDate.of(2026, 3, 16)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported period type");
    }

    @Test
    void invalidEventShouldBeRejectedBeforePersistence() {
        assertThat(service.isProcessableEvent(null, "PostPublished", "event-1", LocalDate.now())).isFalse();
        assertThat(service.isProcessableEvent(uuid(1), " ", "event-1", LocalDate.now())).isFalse();
        assertThat(service.isProcessableEvent(uuid(1), "PostPublished", " ", LocalDate.now())).isFalse();
        assertThat(service.isProcessableEvent(uuid(1), "PostPublished", "event-1", null)).isFalse();
    }

    @Test
    void cappedDeltaShouldNeverPassTarget() {
        assertThat(service.cappedDelta(2, 3, 2)).isEqualTo(3);
        assertThat(service.cappedDelta(2, 3, -1)).isEqualTo(2);
    }
}
