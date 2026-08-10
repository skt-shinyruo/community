package com.nowcoder.community.growth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GrowthBusinessTimeServiceTest {

    @Test
    void todayShouldUseConfiguredBusinessZoneWhenSystemClockUsesUtc() {
        GrowthBusinessTimeService service = new GrowthBusinessTimeService(
                "Asia/Shanghai",
                Clock.fixed(Instant.parse("2026-03-21T16:30:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.today()).isEqualTo(LocalDate.of(2026, 3, 22));
        assertThat(service.dateOf(null)).isEqualTo(LocalDate.of(2026, 3, 22));
    }
}
