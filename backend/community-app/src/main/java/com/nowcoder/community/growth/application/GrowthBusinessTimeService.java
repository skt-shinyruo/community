package com.nowcoder.community.growth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

@Service
public class GrowthBusinessTimeService {

    private static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    private final Clock clock;
    private final ZoneId zoneId;

    public GrowthBusinessTimeService(
            @Value("${growth.business-zone-id:" + DEFAULT_ZONE_ID + "}") String zoneIdValue,
            Clock clock
    ) {
        this.zoneId = resolveZoneId(zoneIdValue);
        this.clock = Objects.requireNonNull(clock, "clock must not be null").withZone(this.zoneId);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate dateOf(Instant instant) {
        Instant resolved = instant == null ? clock.instant() : instant;
        return LocalDate.ofInstant(resolved, zoneId);
    }

    public Date startOfDayDate(LocalDate bizDate) {
        LocalDate resolved = bizDate == null ? today() : bizDate;
        return Date.from(resolved.atStartOfDay(zoneId).toInstant());
    }

    private static ZoneId resolveZoneId(String zoneIdValue) {
        if (zoneIdValue == null || zoneIdValue.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE_ID);
        }
        return ZoneId.of(zoneIdValue.trim());
    }
}
