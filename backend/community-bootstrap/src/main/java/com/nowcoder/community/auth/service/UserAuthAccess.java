package com.nowcoder.community.auth.service;

import com.nowcoder.community.infra.modulecall.ModuleCallOptions;
import com.nowcoder.community.infra.modulecall.ModuleCallSupport;
import com.nowcoder.community.user.api.internal.UserAuthApi;
import com.nowcoder.community.user.api.internal.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalUserByEmailResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserAuthAccess {

    private static final Logger log = LoggerFactory.getLogger(UserAuthAccess.class);
    private static final String TARGET_MODULE = "user";

    private final MeterRegistry meterRegistry;
    private final UserAuthApi userAuthApi;

    public UserAuthAccess(MeterRegistry meterRegistry, UserAuthApi userAuthApi) {
        this.meterRegistry = meterRegistry;
        this.userAuthApi = userAuthApi;
    }

    public UserInternalAuthenticateResponse authenticate(String username, String password) {
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "authenticate",
                () -> userAuthApi.authenticate(username, password),
                failClosed()
        );
    }

    public UserInternalSessionProfileResponse sessionProfile(int userId) {
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "sessionProfile",
                () -> userAuthApi.sessionProfile(userId),
                failClosed()
        );
    }

    public UserInternalRegisterResponse register(String username, String password, String email) {
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "register",
                () -> userAuthApi.register(username, password, email),
                failClosed()
        );
    }

    public int activate(int userId, String activationCode) {
        UserInternalActivationResponse data = ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "activate",
                () -> userAuthApi.activate(userId, activationCode),
                failClosed()
        );
        return data == null ? 2 : data.getResult();
    }

    public UserInternalUserByEmailResponse findByEmailOrNull(String email) {
        // 约定：不存在时 data=null（仍为 OK）
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "byEmail",
                () -> userAuthApi.findByEmailOrNull(email),
                failClosed()
        );
    }

    public void updatePassword(int userId, String newPassword) {
        ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "updatePassword",
                () -> userAuthApi.updatePassword(userId, newPassword),
                failClosed()
        );
    }

    public void storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt) {
        ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "refreshStore",
                () -> userAuthApi.storeRefreshToken(tokenHash, userId, familyId, expiresAt),
                failClosed()
        );
    }

    public UserInternalRefreshTokenRecordResponse findRefreshTokenOrNull(String tokenHash) {
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "refreshFind",
                () -> userAuthApi.findRefreshTokenOrNull(tokenHash),
                failClosed()
        );
    }

    public UserInternalRefreshTokenRecordResponse consumeRefreshToken(String tokenHash) {
        return ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "refreshConsume",
                () -> userAuthApi.consumeRefreshToken(tokenHash),
                failClosed()
        );
    }

    public void revokeRefreshToken(String tokenHash) {
        ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "refreshRevoke",
                () -> userAuthApi.revokeRefreshToken(tokenHash),
                failClosed()
        );
    }

    public void revokeRefreshTokenFamily(String familyId) {
        ModuleCallSupport.callResult(
                meterRegistry,
                TARGET_MODULE,
                "refreshRevokeFamily",
                () -> userAuthApi.revokeRefreshTokenFamily(familyId),
                failClosed()
        );
    }

    private <T> ModuleCallOptions<T> failClosed() {
        return ModuleCallOptions.<T>failClosed().withWarnLogger((m, e) -> log.warn(m, e));
    }
}
