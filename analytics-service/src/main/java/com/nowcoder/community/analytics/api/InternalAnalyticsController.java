package com.nowcoder.community.analytics.api;

import com.nowcoder.community.analytics.service.AnalyticsService;
import com.nowcoder.community.common.api.Result;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/analytics")
public class InternalAnalyticsController {

    private final AnalyticsService analyticsService;

    public InternalAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/uv/record")
    public Result<Void> recordUv(
            @RequestParam @NotBlank String ip,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        analyticsService.recordUv(date == null ? LocalDate.now() : date, ip);
        return Result.ok();
    }

    @PostMapping("/dau/record")
    public Result<Void> recordDau(
            @RequestParam int userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        analyticsService.recordDau(date == null ? LocalDate.now() : date, userId);
        return Result.ok();
    }
}
