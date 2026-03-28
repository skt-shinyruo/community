package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.dto.UserSummary;
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
    void messageUserQueryServiceShouldDependOnUserLookupQueryApi() {
        assertThat(MessageUserQueryService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        Duration.class,
                        int.class,
                        UserLookupQueryApi.class
                ));
    }

    @Test
    void findUserIdByUsernameOrNullShouldUseShortTtlCache() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryByUsername("alice"))
                .thenReturn(new UserSummaryView(123, "alice", "h", 0));

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        Integer id1 = service.findUserIdByUsernameOrNull("alice ");
        Integer id2 = service.findUserIdByUsernameOrNull("alice");

        assertThat(id1).isEqualTo(123);
        assertThat(id2).isEqualTo(123);

        verify(userLookupQueryApi, times(1)).getSummaryByUsername("alice");
    }

    @Test
    void findUserSummaryByIdOrNullShouldReturnNullWhenLookupApiReturnsNull() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryById(404)).thenReturn(null);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        UserSummary summary = service.findUserSummaryByIdOrNull(404);

        assertThat(summary).isNull();
        verify(userLookupQueryApi).getSummaryById(404);
    }

    @Test
    void findUserSummaryByIdOrNullShouldPropagateNonUserNotFoundBusinessException() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        BusinessException exception = new BusinessException(CommonErrorCode.FORBIDDEN, "denied");
        when(userLookupQueryApi.getSummaryById(7)).thenThrow(exception);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        assertThatThrownBy(() -> service.findUserSummaryByIdOrNull(7))
                .isSameAs(exception);
    }

    @Test
    void findUserIdByUsernameOrNullShouldPropagateNonUserNotFoundBusinessException() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "bad username");
        when(userLookupQueryApi.getSummaryByUsername("alice")).thenThrow(exception);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        assertThatThrownBy(() -> service.findUserIdByUsernameOrNull("alice"))
                .isSameAs(exception);
    }

    @Test
    void findUserIdByUsernameOrNullShouldReturnNullWhenLookupApiReturnsNull() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.getSummaryByUsername("alice")).thenReturn(null);

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        Integer userId = service.findUserIdByUsernameOrNull("alice");

        assertThat(userId).isNull();
        verify(userLookupQueryApi).getSummaryByUsername("alice");
    }

    @Test
    void getUserSummariesByIdsShouldMapViewsAndFilterInvalidEntries() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.listSummariesByIds(argThat(ids ->
                ids != null && ids.size() == 2 && ids.containsAll(List.of(2, 3))
        )))
                .thenReturn(Arrays.asList(
                        new UserSummaryView(2, "bob", "h2", 0),
                        null,
                        new UserSummaryView(0, "nobody", "h3", 0)
                ));

        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        Map<Integer, UserSummary> result = service.getUserSummariesByIds(Set.of(0, -1, 2, 3));

        assertThat(result).hasSize(1);
        assertThat(result.get(2)).extracting(UserSummary::getId, UserSummary::getUsername, UserSummary::getHeaderUrl)
                .containsExactly(2, "bob", "h2");
        verify(userLookupQueryApi).listSummariesByIds(argThat(ids ->
                ids != null && ids.size() == 2 && ids.containsAll(List.of(2, 3))
        ));
    }

    @Test
    void getUserSummariesByIdsShouldReturnEmptyMapWhenNoPositiveIdsRemain() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        MessageUserQueryService service = new MessageUserQueryService(
                Duration.ofSeconds(60),
                10,
                userLookupQueryApi
        );

        Map<Integer, UserSummary> result = service.getUserSummariesByIds(Set.of(0, -1));

        assertThat(result).isEmpty();
        verifyNoInteractions(userLookupQueryApi);
    }
}
