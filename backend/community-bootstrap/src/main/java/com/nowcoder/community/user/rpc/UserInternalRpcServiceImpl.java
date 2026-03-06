package com.nowcoder.community.user.rpc;

// user-service 内部 RPC Provider：实现认证/会话相关内部接口（A-1 下为进程内调用，保留契约以便未来拆分）。
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.user.api.rpc.UserInternalRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
public class UserInternalRpcServiceImpl implements UserInternalRpcService {

    private final InternalUserService internalUserService;
    private final RefreshTokenSessionService refreshTokenSessionService;

    public UserInternalRpcServiceImpl(InternalUserService internalUserService, RefreshTokenSessionService refreshTokenSessionService) {
        this.internalUserService = internalUserService;
        this.refreshTokenSessionService = refreshTokenSessionService;
    }

    @Override
    public Result<UserInternalAuthenticateResponse> authenticate(String username, String password) {
        try {
            User user = internalUserService.authenticate(username, password);
            UserInternalAuthenticateResponse resp = new UserInternalAuthenticateResponse();
            resp.setUserId(user.getId());
            resp.setUsername(user.getUsername());
            resp.setStatus(user.getStatus());
            resp.setAuthorities(internalUserService.authoritiesOf(user));
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserInternalSessionProfileResponse> sessionProfile(int userId) {
        try {
            User user = internalUserService.getSessionProfile(userId);
            UserInternalSessionProfileResponse resp = new UserInternalSessionProfileResponse();
            resp.setUserId(user.getId());
            resp.setUsername(user.getUsername());
            resp.setStatus(user.getStatus());
            resp.setAuthorities(internalUserService.authoritiesOf(user));
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserInternalRegisterResponse> register(String username, String password, String email) {
        try {
            User user = internalUserService.register(username, password, email);
            UserInternalRegisterResponse resp = new UserInternalRegisterResponse();
            resp.setUserId(user.getId());
            resp.setActivationCode(user.getActivationCode());
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserInternalActivationResponse> activate(int userId, String activationCode) {
        try {
            int result = internalUserService.activate(userId, safeTrim(activationCode));
            UserInternalActivationResponse resp = new UserInternalActivationResponse();
            resp.setResult(result);
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserInternalUserByEmailResponse> findByEmailOrNull(String email) {
        try {
            User user = internalUserService.findByEmailOrNull(email);
            if (user == null || user.getId() <= 0) {
                return Result.ok(null);
            }
            UserInternalUserByEmailResponse resp = new UserInternalUserByEmailResponse();
            resp.setUserId(user.getId());
            resp.setUsername(user.getUsername());
            resp.setEmail(user.getEmail());
            resp.setStatus(user.getStatus());
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> updatePassword(int userId, String newPassword) {
        try {
            internalUserService.updatePassword(userId, safeTrim(newPassword));
            return Result.ok();
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt) {
        try {
            refreshTokenSessionService.store(safeTrim(tokenHash), userId, safeTrim(familyId), expiresAt);
            return Result.ok();
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserInternalRefreshTokenRecordResponse> findRefreshTokenOrNull(String tokenHash) {
        try {
            RefreshTokenSessionService.RefreshTokenRecord record = refreshTokenSessionService.find(safeTrim(tokenHash));
            if (record == null) {
                return Result.ok(null);
            }
            UserInternalRefreshTokenRecordResponse resp = new UserInternalRefreshTokenRecordResponse();
            resp.setTokenHash(record.tokenHash());
            resp.setUserId(record.userId());
            resp.setFamilyId(record.familyId());
            resp.setExpiresAt(record.expiresAt());
            resp.setRevokedAt(record.revokedAt());
            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> revokeRefreshToken(String tokenHash) {
        try {
            refreshTokenSessionService.revoke(safeTrim(tokenHash));
            return Result.ok();
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> revokeRefreshTokenFamily(String familyId) {
        try {
            refreshTokenSessionService.revokeFamily(safeTrim(familyId));
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

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
