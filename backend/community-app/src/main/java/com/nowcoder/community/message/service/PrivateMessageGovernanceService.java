package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.springframework.stereotype.Service;

@Service
public class PrivateMessageGovernanceService {

    private final UserLookupQueryApi userLookupQueryApi;
    private final UserModerationGuard moderationGuard;
    private final BlockService blockService;

    public PrivateMessageGovernanceService(
            UserLookupQueryApi userLookupQueryApi,
            UserModerationGuard moderationGuard,
            BlockService blockService
    ) {
        this.userLookupQueryApi = userLookupQueryApi;
        this.moderationGuard = moderationGuard;
        this.blockService = blockService;
    }

    public void validateCanSendPrivateMessage(int fromUserId, int toUserId) {
        if (fromUserId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "fromUserId 非法");
        }
        if (toUserId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "toUserId 非法");
        }
        if (fromUserId == toUserId) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "不能给自己发送私信");
        }

        if (userLookupQueryApi.getSummaryById(fromUserId) == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        moderationGuard.assertCanSendMessage(fromUserId);
        if (userLookupQueryApi.getSummaryById(toUserId) == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }
        if (blockService != null && blockService.isEitherBlocked(fromUserId, toUserId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "双方存在拉黑关系，无法发送私信");
        }
    }
}
