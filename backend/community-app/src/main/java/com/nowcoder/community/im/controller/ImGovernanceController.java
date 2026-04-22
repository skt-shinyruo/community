package com.nowcoder.community.im.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.infra.security.auth.CurrentUser;
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * IM 私信发送治理校验（供 im-realtime 等服务侧转发用户 JWT 调用）。
 *
 * <p>约定：fromUserId 必须从 JWT.sub 派生（不信任请求体）。</p>
 */
@RestController
@RequestMapping("/api/im-governance")
public class ImGovernanceController {

    private final PrivateMessageGovernanceActionApi governanceActionApi;

    public ImGovernanceController(PrivateMessageGovernanceActionApi governanceActionApi) {
        this.governanceActionApi = governanceActionApi;
    }

    @PostMapping("/private-messages/validate")
    public Result<Void> validateSendPrivateMessage(Authentication authentication, @RequestBody ValidatePrivateMessageRequest request) {
        UUID fromUserId = CurrentUser.requireUserUuid(authentication);
        UUID toUserId = request == null ? null : request.toUserId();

        governanceActionApi.validateCanSendPrivateMessage(fromUserId, toUserId);

        return Result.ok();
    }

    public record ValidatePrivateMessageRequest(UUID toUserId) {
    }
}
