package com.nowcoder.community.growth.infrastructure.api;

import com.nowcoder.community.growth.api.model.UserLevelSummaryView;
import com.nowcoder.community.growth.api.query.UserLevelQueryApi;
import com.nowcoder.community.growth.application.UserLevelApplicationService;
import com.nowcoder.community.growth.application.result.UserLevelSummaryResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserLevelQueryApiAdapter implements UserLevelQueryApi {

    private final UserLevelApplicationService applicationService;

    public UserLevelQueryApiAdapter(UserLevelApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public UserLevelSummaryView evaluateLevel(UUID userId) {
        return toView(applicationService.evaluateLevel(userId));
    }

    private UserLevelSummaryView toView(UserLevelSummaryResult result) {
        return new UserLevelSummaryView(
                result.userLevel(),
                result.signInDaysInWindow(),
                result.windowDays(),
                result.lv2Threshold(),
                result.lv3Threshold(),
                result.enabled()
        );
    }
}
