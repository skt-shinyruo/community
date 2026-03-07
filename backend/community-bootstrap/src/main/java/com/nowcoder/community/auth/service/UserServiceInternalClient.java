package com.nowcoder.community.auth.service;

import com.nowcoder.community.infra.internalclient.InternalCallOptions;
import com.nowcoder.community.infra.internalclient.InternalClientSupport;
import com.nowcoder.community.user.api.rpc.UserInternalRpcService;
import com.nowcoder.community.user.api.rpc.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserServiceInternalClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceInternalClient.class);
    private static final String TARGET = "user-service";

    private final MeterRegistry meterRegistry;
    private final UserInternalRpcService userInternalRpcService;

    public UserServiceInternalClient(MeterRegistry meterRegistry, UserInternalRpcService userInternalRpcService) {
        this.meterRegistry = meterRegistry;
        this.userInternalRpcService = userInternalRpcService;
    }

    public UserInternalAuthenticateResponse authenticate(String username, String password) {
        return InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "authenticate",
                () -> userInternalRpcService.authenticate(username, password),
                failClosed()
        );
    }

    public UserInternalSessionProfileResponse sessionProfile(int userId) {
        return InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "sessionProfile",
                () -> userInternalRpcService.sessionProfile(userId),
                failClosed()
        );
    }

    public UserInternalRegisterResponse register(String username, String password, String email) {
        return InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "register",
                () -> userInternalRpcService.register(username, password, email),
                failClosed()
        );
    }

    public int activate(int userId, String activationCode) {
        UserInternalActivationResponse data = InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "activate",
                () -> userInternalRpcService.activate(userId, activationCode),
                failClosed()
        );
        return data == null ? 2 : data.getResult();
    }

    public UserInternalUserByEmailResponse findByEmailOrNull(String email) {
        // 约定：不存在时 data=null（仍为 OK）
        return InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "byEmail",
                () -> userInternalRpcService.findByEmailOrNull(email),
                failClosed()
        );
    }

    public void updatePassword(int userId, String newPassword) {
        InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "updatePassword",
                () -> userInternalRpcService.updatePassword(userId, newPassword),
                failClosed()
        );
    }

    public void storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt) {
        InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "refreshStore",
                () -> userInternalRpcService.storeRefreshToken(tokenHash, userId, familyId, expiresAt),
                failClosed()
        );
    }

    public UserInternalRefreshTokenRecordResponse findRefreshTokenOrNull(String tokenHash) {
        return InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "refreshFind",
                () -> userInternalRpcService.findRefreshTokenOrNull(tokenHash),
                failClosed()
        );
    }

    public void revokeRefreshToken(String tokenHash) {
        InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "refreshRevoke",
                () -> userInternalRpcService.revokeRefreshToken(tokenHash),
                failClosed()
        );
    }

    public void revokeRefreshTokenFamily(String familyId) {
        InternalClientSupport.callResult(
                meterRegistry,
                TARGET,
                "refreshRevokeFamily",
                () -> userInternalRpcService.revokeRefreshTokenFamily(familyId),
                failClosed()
        );
    }

    private <T> InternalCallOptions<T> failClosed() {
        return InternalCallOptions.<T>failClosed().withWarnLogger((m, e) -> log.warn(m, e));
    }
}
