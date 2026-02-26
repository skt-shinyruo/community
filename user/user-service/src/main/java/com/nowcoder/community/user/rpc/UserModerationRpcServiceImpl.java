package com.nowcoder.community.user.rpc;

// user-service 的 Dubbo Provider：实现治理相关 RPC（处罚状态查询/扫描/应用），供下游投影 bootstrap 与写路径校验使用。
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.rpc.UserModerationRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserModerationStatus;
import com.nowcoder.community.user.service.InternalUserService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@DubboService
public class UserModerationRpcServiceImpl implements UserModerationRpcService {

    private final InternalUserService internalUserService;

    public UserModerationRpcServiceImpl(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @Override
    public Result<UserModerationStatus> getStatus(int userId) {
        try {
            InternalUserService.ModerationStatus s = internalUserService.moderationStatus(userId);
            return Result.ok(toDto(s));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<UserModerationStatus>> scanStatuses(int afterId, int limit) {
        try {
            List<InternalUserService.ModerationStatus> rows = internalUserService.scanModerationStatusesAfterId(afterId, limit);
            if (rows == null || rows.isEmpty()) {
                return Result.ok(List.of());
            }
            List<UserModerationStatus> list = new ArrayList<>(rows.size());
            for (InternalUserService.ModerationStatus r : rows) {
                UserModerationStatus dto = toDto(r);
                if (dto != null && dto.getUserId() > 0) {
                    list.add(dto);
                }
            }
            return Result.ok(list);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserModerationStatus> applyModeration(int userId, String action, int durationSeconds) {
        try {
            InternalUserService.ModerationStatus s = internalUserService.applyModeration(userId, action, durationSeconds);
            return Result.ok(toDto(s));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private UserModerationStatus toDto(InternalUserService.ModerationStatus status) {
        if (status == null) {
            return null;
        }
        UserModerationStatus dto = new UserModerationStatus();
        dto.setUserId(status.getUserId());
        dto.setMuteUntil(status.getMuteUntil());
        dto.setBanUntil(status.getBanUntil());
        return dto;
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

