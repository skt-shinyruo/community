package com.nowcoder.community.user.api.query;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.exception.UserErrorCode;

import java.util.List;
import java.util.UUID;

public interface UserLookupQueryApi {

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView getSummaryById(UUID userId);

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView getSummaryByUsername(String username);

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView findSummaryByEmailOrNull(String email);

    List<UserSummaryView> listSummariesByIds(List<UUID> userIds);

    default UserSummaryView requireSummaryById(UUID userId) {
        UserSummaryView summaryView = getSummaryById(userId);
        if (summaryView == null || summaryView.id() == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return summaryView;
    }

    default UserSummaryView requireSummaryById(int userId) {
        throw new BusinessException(com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT, "numeric userId 已不再受支持");
    }

    default UserSummaryView requireSummaryByUsername(String username) {
        UserSummaryView summaryView = getSummaryByUsername(username);
        if (summaryView == null || summaryView.id() == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return summaryView;
    }
}
