package com.nowcoder.community.user.controller;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.user.application.UserAvatarApplicationService;
import com.nowcoder.community.user.application.UserReadApplicationService;
import com.nowcoder.community.user.application.result.UserSummaryResult;
import com.nowcoder.community.user.controller.dto.BatchUserSummaryRequest;
import com.nowcoder.community.user.controller.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerUnitTest {

    @Test
    void batchSummaryShouldPreserveRequestOrderUsingApplicationServiceResponses() {
        UserReadApplicationService userReadApplicationService = mock(UserReadApplicationService.class);
        UserController controller = new UserController(
                userReadApplicationService,
                mock(UserAvatarApplicationService.class)
        );
        UUID aliceId = uuid(7);
        UUID bobId = uuid(9);
        BatchUserSummaryRequest request = new BatchUserSummaryRequest();
        request.setUserIds(Arrays.asList(aliceId, bobId, aliceId, null));
        when(userReadApplicationService.listSummaryResultsByIds(Arrays.asList(aliceId, bobId, aliceId, null)))
                .thenReturn(List.of(
                        new UserSummaryResult(aliceId, "alice", "h7", 1),
                        new UserSummaryResult(bobId, "bob", "h9", 2)
                ));

        Result<List<UserSummaryResponse>> result = controller.batchSummary(request);

        assertThat(result.getData()).extracting(UserSummaryResponse::getId).containsExactly(aliceId, bobId);
        verify(userReadApplicationService).listSummaryResultsByIds(Arrays.asList(aliceId, bobId, aliceId, null));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
