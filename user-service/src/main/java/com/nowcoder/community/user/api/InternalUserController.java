package com.nowcoder.community.user.api;

import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.user.api.dto.InternalActivateRequest;
import com.nowcoder.community.user.api.dto.InternalActivationResponse;
import com.nowcoder.community.user.api.dto.InternalAuthenticateRequest;
import com.nowcoder.community.user.api.dto.InternalAuthenticateResponse;
import com.nowcoder.community.user.api.dto.InternalModerationApplyRequest;
import com.nowcoder.community.user.api.dto.InternalModerationStatusResponse;
import com.nowcoder.community.user.api.dto.InternalRegisterRequest;
import com.nowcoder.community.user.api.dto.InternalRegisterResponse;
import com.nowcoder.community.user.api.dto.InternalSessionProfileResponse;
import com.nowcoder.community.user.api.dto.InternalUpdatePasswordRequest;
import com.nowcoder.community.user.api.dto.InternalUserByEmailResponse;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.service.InternalUserService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final InternalUserService internalUserService;

    public InternalUserController(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @PostMapping("/authenticate")
    public Result<InternalAuthenticateResponse> authenticate(
            @Valid @RequestBody InternalAuthenticateRequest request
    ) {
        User user = internalUserService.authenticate(request.getUsername(), request.getPassword());
        InternalAuthenticateResponse resp = new InternalAuthenticateResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setStatus(user.getStatus());
        resp.setAuthorities(internalUserService.authoritiesOf(user));
        return Result.ok(resp);
    }

    @GetMapping("/{userId}/session-profile")
    public Result<InternalSessionProfileResponse> sessionProfile(
            @PathVariable int userId
    ) {
        User user = internalUserService.getSessionProfile(userId);
        InternalSessionProfileResponse resp = new InternalSessionProfileResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setStatus(user.getStatus());
        resp.setAuthorities(internalUserService.authoritiesOf(user));
        return Result.ok(resp);
    }

    @PostMapping("/register")
    public Result<InternalRegisterResponse> register(
            @Valid @RequestBody InternalRegisterRequest request
    ) {
        User user = internalUserService.register(request.getUsername(), request.getPassword(), request.getEmail());
        InternalRegisterResponse resp = new InternalRegisterResponse();
        resp.setUserId(user.getId());
        resp.setActivationCode(user.getActivationCode());
        return Result.ok(resp);
    }

    @PostMapping("/{userId}/activate")
    public Result<InternalActivationResponse> activate(
            @PathVariable int userId,
            @Valid @RequestBody InternalActivateRequest request
    ) {
        String code = request.getActivationCode();
        int result = internalUserService.activate(userId, StringUtils.hasText(code) ? code.trim() : "");
        InternalActivationResponse resp = new InternalActivationResponse();
        resp.setResult(result);
        return Result.ok(resp);
    }

    @GetMapping("/by-email")
    public Result<InternalUserByEmailResponse> byEmail(
            @RequestParam String email
    ) {
        User user = internalUserService.findByEmailOrNull(email);
        if (user == null || user.getId() <= 0) {
            return Result.ok(null);
        }
        InternalUserByEmailResponse resp = new InternalUserByEmailResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setEmail(user.getEmail());
        resp.setStatus(user.getStatus());
        return Result.ok(resp);
    }

    @PostMapping("/{userId}/password")
    public Result<Void> updatePassword(
            @PathVariable int userId,
            @Valid @RequestBody InternalUpdatePasswordRequest request
    ) {
        internalUserService.updatePassword(userId, request.getNewPassword());
        return Result.ok();
    }

    @GetMapping("/{userId}/moderation-status")
    public Result<InternalModerationStatusResponse> moderationStatus(
            @PathVariable int userId
    ) {
        InternalUserService.ModerationStatus s = internalUserService.moderationStatus(userId);
        InternalModerationStatusResponse resp = new InternalModerationStatusResponse();
        resp.setUserId(s.getUserId());
        resp.setMuteUntil(s.getMuteUntil());
        resp.setBanUntil(s.getBanUntil());
        return Result.ok(resp);
    }

    @PostMapping("/{userId}/moderation")
    public Result<InternalModerationStatusResponse> moderationApply(
            @PathVariable int userId,
            @Valid @RequestBody InternalModerationApplyRequest request
    ) {
        InternalUserService.ModerationStatus s = internalUserService.applyModeration(userId, request.getAction(), request.getDurationSeconds());
        InternalModerationStatusResponse resp = new InternalModerationStatusResponse();
        resp.setUserId(s.getUserId());
        resp.setMuteUntil(s.getMuteUntil());
        resp.setBanUntil(s.getBanUntil());
        return Result.ok(resp);
    }
}
