package com.nowcoder.community.user.api.rpc;

// user-service 内部 RPC 接口：供 auth-service 等服务调用，替代原先的 HTTP `/internal/users/**` 调用。
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.user.api.rpc.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.rpc.dto.UserInternalUserByEmailResponse;

import java.time.Instant;

public interface UserInternalRpcService {

    Result<UserInternalAuthenticateResponse> authenticate(String username, String password);

    Result<UserInternalSessionProfileResponse> sessionProfile(int userId);

    Result<UserInternalRegisterResponse> register(String username, String password, String email);

    Result<UserInternalActivationResponse> activate(int userId, String activationCode);

    Result<UserInternalUserByEmailResponse> findByEmailOrNull(String email);

    Result<Void> updatePassword(int userId, String newPassword);

    Result<Void> storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt);

    Result<UserInternalRefreshTokenRecordResponse> findRefreshTokenOrNull(String tokenHash);

    Result<Void> revokeRefreshToken(String tokenHash);

    Result<Void> revokeRefreshTokenFamily(String familyId);
}

