package com.nowcoder.community.im.api;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.message.service.UserModerationGuard;
import com.nowcoder.community.social.application.BlockQueryApplicationService;
import com.nowcoder.community.user.api.UserErrorCode;
import com.nowcoder.community.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IM 私信发送治理校验（供 im-realtime 等服务侧转发用户 JWT 调用）。
 *
 * <p>约定：fromUserId 必须从 JWT.sub 派生（不信任请求体）。</p>
 */
@RestController
@RequestMapping("/api/im-governance")
public class ImGovernanceController {

    private final UserService userService;
    private final UserModerationGuard moderationGuard;
    private final BlockQueryApplicationService blockQueryApplicationService;

    public ImGovernanceController(
            UserService userService,
            UserModerationGuard moderationGuard,
            BlockQueryApplicationService blockQueryApplicationService
    ) {
        this.userService = userService;
        this.moderationGuard = moderationGuard;
        this.blockQueryApplicationService = blockQueryApplicationService;
    }

    @PostMapping("/private-messages/validate")
    public Result<Void> validateSendPrivateMessage(Authentication authentication, @RequestBody ValidatePrivateMessageRequest request) {
        int fromUserId = CurrentUser.requireUserId(authentication);
        int toUserId = request == null ? 0 : Math.max(0, request.toUserId());

        if (fromUserId <= 0) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        if (toUserId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toUserId 非法");
        }
        if (toUserId == fromUserId) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信");
        }

        // Ensure users exist (avoid persisting messages to non-existing users).
        userService.getById(fromUserId);
        try {
            userService.getById(toUserId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在");
            }
            throw e;
        }

        // Mute/ban checks.
        moderationGuard.assertCanSendMessage(fromUserId);

        // Block checks.
        if (blockQueryApplicationService != null && blockQueryApplicationService.isEitherBlocked(fromUserId, toUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "双方存在拉黑关系，无法发送私信");
        }

        return Result.ok();
    }

    public record ValidatePrivateMessageRequest(int toUserId) {
    }
}
