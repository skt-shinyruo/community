package com.nowcoder.community.im.governance;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PrivateMessageGovernanceService implements PrivateMessageGovernanceActionApi {

    private final UserLookupQueryApi userLookupQueryApi;
    private final UserModerationGuard moderationGuard;
    private final SocialBlockQueryApi blockQueryApi;

    public PrivateMessageGovernanceService(
            UserLookupQueryApi userLookupQueryApi,
            UserModerationGuard moderationGuard,
            SocialBlockQueryApi blockQueryApi
    ) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.moderationGuard = moderationGuard;
        this.blockQueryApi = blockQueryApi;
    }

    @Override
    public void validateCanSendPrivateMessage(UUID fromUserId, UUID toUserId) {
        if (fromUserId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "fromUserId 非法");
        }
        if (toUserId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toUserId 非法");
        }
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信");
        }

        userLookupQueryApi.requireSummaryById(fromUserId);
        moderationGuard.assertCanSendMessage(fromUserId);
        userLookupQueryApi.requireSummaryById(toUserId);
        if (blockQueryApi != null && blockQueryApi.isEitherBlocked(fromUserId, toUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "双方存在拉黑关系，无法发送私信");
        }
    }
}
