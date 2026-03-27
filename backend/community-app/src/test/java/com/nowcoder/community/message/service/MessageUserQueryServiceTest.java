package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.dto.UserSummary;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.service.UserQueryService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageUserQueryServiceTest {

    @Test
    void findUserIdByUsernameOrNullShouldUseShortTtlCache() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        User user = new User();
        user.setId(123);
        user.setUsername("alice");
        user.setHeaderUrl("h");
        when(userQueryService.getByUsername("alice")).thenReturn(user);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        Integer id1 = service.findUserIdByUsernameOrNull("alice ");
        Integer id2 = service.findUserIdByUsernameOrNull("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(userQueryService, times(1)).getByUsername("alice");
    }

    @Test
    void findUserSummaryByIdOrNullShouldReturnNullWhenUserNotFound() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        when(userQueryService.getById(404)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        UserSummary summary = service.findUserSummaryByIdOrNull(404);

        assertThat(summary).isNull();
        verify(userQueryService).getById(404);
    }

    @Test
    void findUserSummaryByIdOrNullShouldPropagateNonUserNotFoundBusinessException() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        BusinessException exception = new BusinessException(CommonErrorCode.FORBIDDEN, "denied");
        when(userQueryService.getById(7)).thenThrow(exception);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        assertThatThrownBy(() -> service.findUserSummaryByIdOrNull(7))
                .isSameAs(exception);
    }

    @Test
    void findUserIdByUsernameOrNullShouldPropagateNonUserNotFoundBusinessException() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad username");
        when(userQueryService.getByUsername("alice")).thenThrow(exception);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        assertThatThrownBy(() -> service.findUserIdByUsernameOrNull("alice"))
                .isSameAs(exception);
    }

    @Test
    void findUserIdByUsernameOrNullShouldReturnNullWhenUserNotFound() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        when(userQueryService.getByUsername("alice")).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        Integer userId = service.findUserIdByUsernameOrNull("alice");

        assertThat(userId).isNull();
        verify(userQueryService).getByUsername("alice");
    }

    @Test
    void getUserSummariesByIdsShouldMapUsersAndFilterInvalidEntries() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        User validUser = new User();
        validUser.setId(2);
        validUser.setUsername("bob");
        validUser.setHeaderUrl("h2");

        User invalidUser = new User();
        invalidUser.setId(0);
        invalidUser.setUsername("nobody");

        when(userQueryService.listUserSummariesByIds(argThat(ids ->
                ids != null && ids.size() == 2 && ids.containsAll(List.of(2, 3))
        )))
                .thenReturn(Arrays.asList(validUser, null, invalidUser));

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        Map<Integer, UserSummary> result = service.getUserSummariesByIds(Set.of(0, -1, 2, 3));

        assertThat(result).hasSize(1);
        assertThat(result.get(2)).extracting(UserSummary::getId, UserSummary::getUsername, UserSummary::getHeaderUrl)
                .containsExactly(2, "bob", "h2");
        verify(userQueryService).listUserSummariesByIds(argThat(ids ->
                ids != null && ids.size() == 2 && ids.containsAll(List.of(2, 3))
        ));
    }

    @Test
    void getUserSummariesByIdsShouldReturnEmptyMapWhenNoPositiveIdsRemain() {
        UserQueryService userQueryService = mock(UserQueryService.class);
        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userQueryService
        );

        Map<Integer, UserSummary> result = service.getUserSummariesByIds(Set.of(0, -1));

        assertThat(result).isEmpty();
        verifyNoInteractions(userQueryService);
    }
}
