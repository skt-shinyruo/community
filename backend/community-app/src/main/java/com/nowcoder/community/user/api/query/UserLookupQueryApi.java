package com.nowcoder.community.user.api.query;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.exception.UserErrorCode;

import java.util.List;

public interface UserLookupQueryApi {

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView getSummaryById(int userId);

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView getSummaryByUsername(String username);

    /**
     * @return summary view, or null when the user does not exist
     */
    UserSummaryView findSummaryByEmailOrNull(String email);

    List<UserSummaryView> listSummariesByIds(List<Integer> userIds);

    default UserSummaryView requireSummaryById(int userId) {
        UserSummaryView summaryView = getSummaryById(userId);
        if (summaryView == null || summaryView.id() <= 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return summaryView;
    }

    default UserSummaryView requireSummaryByUsername(String username) {
        UserSummaryView summaryView = getSummaryByUsername(username);
        if (summaryView == null || summaryView.id() <= 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return summaryView;
    }
}
