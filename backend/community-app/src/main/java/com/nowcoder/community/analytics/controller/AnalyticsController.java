package com.nowcoder.community.analytics.controller;

import com.nowcoder.community.analytics.application.AnalyticsApplicationService;
import com.nowcoder.community.analytics.application.command.AnalyticsRangeQuery;
import com.nowcoder.community.common.web.Result;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsApplicationService analyticsApplicationService;

    public AnalyticsController(AnalyticsApplicationService analyticsApplicationService) {
        this.analyticsApplicationService = analyticsApplicationService;
    }

    @GetMapping("/uv")
    public Result<Long> uv(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return Result.ok(analyticsApplicationService.calculateUv(new AnalyticsRangeQuery(start, end)));
    }

    @GetMapping("/dau")
    public Result<Long> dau(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return Result.ok(analyticsApplicationService.calculateDau(new AnalyticsRangeQuery(start, end)));
    }
}
