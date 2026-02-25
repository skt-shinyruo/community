package com.nowcoder.community.analytics.rpc;

import com.nowcoder.community.analytics.api.rpc.InternalAnalyticsRpcService;
import com.nowcoder.community.analytics.service.AnalyticsService;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;

@DubboService
public class InternalAnalyticsRpcServiceImpl implements InternalAnalyticsRpcService {

    private final AnalyticsService analyticsService;

    public InternalAnalyticsRpcServiceImpl(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public Result<Void> recordUv(String ip, LocalDate date) {
        try {
            if (!StringUtils.hasText(ip)) {
                throw new BusinessException(INVALID_ARGUMENT, "ip 不能为空");
            }
            LocalDate d = date == null ? LocalDate.now() : date;
            analyticsService.recordUv(d, ip.trim());
            return Result.ok();
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> recordDau(int userId, LocalDate date) {
        try {
            if (userId <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
            }
            LocalDate d = date == null ? LocalDate.now() : date;
            analyticsService.recordDau(d, userId);
            return Result.ok();
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}

