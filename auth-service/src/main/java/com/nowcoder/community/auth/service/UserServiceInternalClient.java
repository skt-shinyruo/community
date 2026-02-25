package com.nowcoder.community.auth.service;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.platform.web.internalclient.InternalClientSupport;
import com.nowcoder.community.user.api.rpc.UserInternalRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Supplier;

@Service
public class UserServiceInternalClient {

    private static final String METRIC_CLIENT = "auth-service:user-service";
    private static final String SERVICE_NAME = "user-service";

    private final MeterRegistry meterRegistry;

    @DubboReference(check = false, retries = 0, timeout = 3000)
    private UserInternalRpcService userInternalRpcService;

    public UserServiceInternalClient(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public UserInternalAuthenticateResponse authenticate(String username, String password) {
        return call("authenticate", () -> {
            Result<UserInternalAuthenticateResponse> result = userInternalRpcService.authenticate(username, password);
            return InternalClientSupport.unwrap(result, SERVICE_NAME);
        });
    }

    public UserInternalSessionProfileResponse sessionProfile(int userId) {
        return call("sessionProfile", () -> {
            Result<UserInternalSessionProfileResponse> result = userInternalRpcService.sessionProfile(userId);
            return InternalClientSupport.unwrap(result, SERVICE_NAME);
        });
    }

    public UserInternalRegisterResponse register(String username, String password, String email) {
        return call("register", () -> {
            Result<UserInternalRegisterResponse> result = userInternalRpcService.register(username, password, email);
            return InternalClientSupport.unwrap(result, SERVICE_NAME);
        });
    }

    public int activate(int userId, String activationCode) {
        return call("activate", () -> {
            Result<UserInternalActivationResponse> result = userInternalRpcService.activate(userId, activationCode);
            UserInternalActivationResponse data = InternalClientSupport.unwrap(result, SERVICE_NAME);
            return data == null ? 2 : data.getResult();
        });
    }

    public UserInternalUserByEmailResponse findByEmailOrNull(String email) {
        return call("byEmail", () -> {
            // 约定：不存在时 data=null（仍为 OK）
            Result<UserInternalUserByEmailResponse> result = userInternalRpcService.findByEmailOrNull(email);
            return InternalClientSupport.unwrap(result, SERVICE_NAME);
        });
    }

    public void updatePassword(int userId, String newPassword) {
        call("updatePassword", () -> {
            Result<Void> result = userInternalRpcService.updatePassword(userId, newPassword);
            InternalClientSupport.unwrap(result, SERVICE_NAME);
            return null;
        });
    }

    public void storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt) {
        call("refreshStore", () -> {
            Result<Void> result = userInternalRpcService.storeRefreshToken(tokenHash, userId, familyId, expiresAt);
            InternalClientSupport.unwrap(result, SERVICE_NAME);
            return null;
        });
    }

    public UserInternalRefreshTokenRecordResponse findRefreshTokenOrNull(String tokenHash) {
        return call("refreshFind", () -> {
            Result<UserInternalRefreshTokenRecordResponse> result = userInternalRpcService.findRefreshTokenOrNull(tokenHash);
            return InternalClientSupport.unwrap(result, SERVICE_NAME);
        });
    }

    public void revokeRefreshToken(String tokenHash) {
        call("refreshRevoke", () -> {
            Result<Void> result = userInternalRpcService.revokeRefreshToken(tokenHash);
            InternalClientSupport.unwrap(result, SERVICE_NAME);
            return null;
        });
    }

    public void revokeRefreshTokenFamily(String familyId) {
        call("refreshRevokeFamily", () -> {
            Result<Void> result = userInternalRpcService.revokeRefreshTokenFamily(familyId);
            InternalClientSupport.unwrap(result, SERVICE_NAME);
            return null;
        });
    }

    private <T> T call(String api, Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            T v = supplier.get();
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_SUCCESS, start);
            return v;
        } catch (BusinessException e) {
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, "biz_error", start);
            throw e;
        } catch (RpcException e) {
            String outcome = e.isTimeout() ? InternalClientSupport.OUTCOME_TIMEOUT : InternalClientSupport.OUTCOME_ERROR;
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, outcome, start);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "user-service 不可用");
        } catch (RuntimeException e) {
            InternalClientSupport.record(meterRegistry, METRIC_CLIENT, api, InternalClientSupport.OUTCOME_ERROR, start);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "user-service 调用失败");
        }
    }
}
