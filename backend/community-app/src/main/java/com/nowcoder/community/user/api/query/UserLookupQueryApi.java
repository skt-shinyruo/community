package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserSummaryView;

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
}
