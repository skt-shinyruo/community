package com.nowcoder.community.growth.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.growth.dto.CheckInActionResponse;
import com.nowcoder.community.growth.dto.CheckInCalendarResponse;
import com.nowcoder.community.growth.dto.CheckInStatusResponse;
import com.nowcoder.community.growth.service.CheckInService;
import com.nowcoder.community.growth.service.GrowthBusinessTimeService;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/growth/check-in")
public class GrowthCenterController {

    private final CheckInService checkInService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public GrowthCenterController(CheckInService checkInService, GrowthBusinessTimeService growthBusinessTimeService) {
        this.checkInService = checkInService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @PostMapping
    public Result<CheckInActionResponse> checkIn(Authentication authentication) {
        int userId = CurrentUser.requireUserId(authentication);
        LocalDate bizDate = growthBusinessTimeService.today();
        CheckInService.CheckInResult result = checkInService.checkIn(userId, bizDate);

        CheckInActionResponse resp = new CheckInActionResponse();
        resp.setNewlyCheckedIn(result.newlyCheckedIn());
        resp.setCheckedInToday(result.checkedInToday());
        resp.setCurrentStreak(result.currentStreak());
        resp.setMaxStreak(result.maxStreak());
        resp.setTotalCheckInDays(result.totalCheckInDays());
        return Result.ok(resp);
    }

    @GetMapping("/status")
    public Result<CheckInStatusResponse> status(
            Authentication authentication,
            @RequestParam(required = false) LocalDate date
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        LocalDate bizDate = date == null ? growthBusinessTimeService.today() : date;
        CheckInService.CheckInStatus status = checkInService.status(userId, bizDate);

        CheckInStatusResponse resp = new CheckInStatusResponse();
        resp.setCheckedInToday(status.checkedInToday());
        resp.setCurrentStreak(status.currentStreak());
        resp.setMaxStreak(status.maxStreak());
        resp.setTotalCheckInDays(status.totalCheckInDays());
        return Result.ok(resp);
    }

    @GetMapping("/calendar")
    public Result<CheckInCalendarResponse> calendar(
            Authentication authentication,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        int userId = CurrentUser.requireUserId(authentication);
        YearMonth yearMonth = year == null || month == null ? growthBusinessTimeService.currentYearMonth() : YearMonth.of(year, month);
        List<LocalDate> dates = checkInService.calendar(userId, yearMonth.getYear(), yearMonth.getMonthValue());

        CheckInCalendarResponse resp = new CheckInCalendarResponse();
        resp.setYear(yearMonth.getYear());
        resp.setMonth(yearMonth.getMonthValue());
        resp.setCheckedInDates(dates);
        return Result.ok(resp);
    }
}
