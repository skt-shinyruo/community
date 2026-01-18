package com.nowcoder.community.analytics.api;

import com.nowcoder.community.analytics.service.AnalyticsService;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;

@RestController
@RequestMapping("/internal/analytics")
public class InternalAnalyticsController {

    private final AnalyticsService analyticsService;
    private final String internalToken;

    public InternalAnalyticsController(AnalyticsService analyticsService, @Value("${analytics.internal-token:}") String internalToken) {
        this.analyticsService = analyticsService;
        this.internalToken = internalToken;
    }

    @PostMapping("/uv/record")
    public Result<Void> recordUv(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestParam @NotBlank String ip,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        assertInternalToken(token);
        analyticsService.recordUv(date == null ? LocalDate.now() : date, ip);
        return Result.ok();
    }

    @PostMapping("/dau/record")
    public Result<Void> recordDau(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestParam int userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        assertInternalToken(token);
        analyticsService.recordDau(date == null ? LocalDate.now() : date, userId);
        return Result.ok();
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "internal-token 未配置");
        }
        if (!internalToken.equals(token)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "internal-token 无效");
        }
    }
}

