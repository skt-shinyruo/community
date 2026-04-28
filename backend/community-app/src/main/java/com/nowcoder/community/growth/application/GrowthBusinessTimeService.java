package com.nowcoder.community.growth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;

@Service
public class GrowthBusinessTimeService {

    private static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    private final Clock clock;
    private final ZoneId zoneId;

    @Autowired
    public GrowthBusinessTimeService(@Value("${growth.business-zone-id:" + DEFAULT_ZONE_ID + "}") String zoneIdValue) {
        this(resolveZoneId(zoneIdValue));
    }

    public GrowthBusinessTimeService(Clock clock, ZoneId zoneId) {
        this.zoneId = zoneId == null ? ZoneId.of(DEFAULT_ZONE_ID) : zoneId;
        this.clock = clock == null ? Clock.system(this.zoneId) : clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public YearMonth currentYearMonth() {
        return YearMonth.from(today());
    }

    public LocalDate dateOf(Instant instant) {
        Instant resolved = instant == null ? clock.instant() : instant;
        return LocalDate.ofInstant(resolved, zoneId);
    }

    public Date startOfDayDate(LocalDate bizDate) {
        LocalDate resolved = bizDate == null ? today() : bizDate;
        return Date.from(resolved.atStartOfDay(zoneId).toInstant());
    }

    private GrowthBusinessTimeService(ZoneId zoneId) {
        this(Clock.system(zoneId), zoneId);
    }

    private static ZoneId resolveZoneId(String zoneIdValue) {
        if (zoneIdValue == null || zoneIdValue.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE_ID);
        }
        return ZoneId.of(zoneIdValue.trim());
    }
}
