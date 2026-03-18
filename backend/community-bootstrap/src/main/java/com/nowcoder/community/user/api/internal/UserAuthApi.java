package com.nowcoder.community.user.api.internal;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.user.api.internal.dto.UserInternalActivationResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalAuthenticateResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalRegisterResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalSessionProfileResponse;
import com.nowcoder.community.user.api.internal.dto.UserInternalUserByEmailResponse;

import java.time.Instant;

/**
 * user 模块对内认证/会话能力：
 * - 供 auth 模块调用；
 * - 约束依赖方向：调用方不得直接依赖 user.service 等实现包。
 */
public interface UserAuthApi {

    Result<UserInternalAuthenticateResponse> authenticate(String username, String password);

    Result<UserInternalSessionProfileResponse> sessionProfile(int userId);

    Result<UserInternalRegisterResponse> register(String username, String password, String email);

    Result<UserInternalActivationResponse> activate(int userId, String activationCode);

    Result<UserInternalUserByEmailResponse> findByEmailOrNull(String email);

    Result<Void> updatePassword(int userId, String newPassword);

    Result<Void> storeRefreshToken(String tokenHash, int userId, String familyId, Instant expiresAt);

    Result<UserInternalRefreshTokenRecordResponse> findRefreshTokenOrNull(String tokenHash);

    Result<UserInternalRefreshTokenRecordResponse> consumeRefreshToken(String tokenHash);

    Result<Void> revokeRefreshToken(String tokenHash);

    Result<Void> revokeRefreshTokenFamily(String familyId);
}
