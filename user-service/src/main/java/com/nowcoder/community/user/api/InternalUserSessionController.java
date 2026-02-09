package com.nowcoder.community.user.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.user.api.dto.InternalRefreshTokenRecordResponse;
import com.nowcoder.community.user.api.dto.InternalRefreshTokenRevokeFamilyRequest;
import com.nowcoder.community.user.api.dto.InternalRefreshTokenRevokeRequest;
import com.nowcoder.community.user.api.dto.InternalRefreshTokenStoreRequest;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * user-service 会话托管 internal API：
 * - 作为 refresh token 的 SSOT（避免 auth-service 直连 MySQL）
 * - 仅用于服务间调用（部署层应隔离 /internal/** 暴露面）
 */
@RestController
@RequestMapping("/internal/users/sessions")
public class InternalUserSessionController {

    private final RefreshTokenSessionService refreshTokenSessionService;

    public InternalUserSessionController(RefreshTokenSessionService refreshTokenSessionService) {
        this.refreshTokenSessionService = refreshTokenSessionService;
    }

    @PostMapping("/refresh/store")
    public Result<Void> storeRefreshToken(@Valid @RequestBody InternalRefreshTokenStoreRequest request) {
        refreshTokenSessionService.store(request.getTokenHash(), request.getUserId(), request.getFamilyId(), request.getExpiresAt());
        return Result.ok();
    }

    @GetMapping("/refresh/{tokenHash}")
    public Result<InternalRefreshTokenRecordResponse> findRefreshToken(@PathVariable String tokenHash) {
        RefreshTokenSessionService.RefreshTokenRecord record = refreshTokenSessionService.find(tokenHash);
        if (record == null) {
            return Result.ok(null);
        }
        InternalRefreshTokenRecordResponse resp = new InternalRefreshTokenRecordResponse();
        resp.setTokenHash(record.tokenHash());
        resp.setUserId(record.userId());
        resp.setFamilyId(record.familyId());
        resp.setExpiresAt(record.expiresAt());
        resp.setRevokedAt(record.revokedAt());
        return Result.ok(resp);
    }

    @PostMapping("/refresh/revoke")
    public Result<Void> revokeRefreshToken(@Valid @RequestBody InternalRefreshTokenRevokeRequest request) {
        refreshTokenSessionService.revoke(request.getTokenHash());
        return Result.ok();
    }

    @PostMapping("/refresh/revoke-family")
    public Result<Void> revokeRefreshTokenFamily(@Valid @RequestBody InternalRefreshTokenRevokeFamilyRequest request) {
        refreshTokenSessionService.revokeFamily(request.getFamilyId());
        return Result.ok();
    }
}

