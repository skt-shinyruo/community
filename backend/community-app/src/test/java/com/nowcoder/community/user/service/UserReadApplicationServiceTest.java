package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import com.nowcoder.community.user.dto.UserResolveResponse;
import com.nowcoder.community.user.dto.UserSummaryResponse;
import com.nowcoder.community.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadApplicationServiceTest {

    @Mock
    private UserQueryService userQueryService;

    @Test
    void shouldImplementLookupAndProfileApisByDelegatingToUserQueryService() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);
        UUID userId = uuid(7);
        Date createTime = new Date();
        UserSummaryView summary = new UserSummaryView(userId, "alice", "h7", 2);
        UserProfileView profile = new UserProfileView(userId, "alice", "h7", 2, 0, createTime, 250, 3, 900L, "ACTIVE");
        when(userQueryService.getSummaryById(userId)).thenReturn(summary);
        when(userQueryService.getSummaryByUsername("alice")).thenReturn(summary);
        when(userQueryService.findSummaryByEmailOrNull("alice@example.com")).thenReturn(summary);
        when(userQueryService.listSummariesByIds(List.of(userId))).thenReturn(List.of(summary));
        when(userQueryService.getProfile(userId)).thenReturn(profile);

        assertThat(service).isInstanceOf(UserLookupQueryApi.class);
        assertThat(service).isInstanceOf(UserProfileQueryApi.class);
        assertThat(service.getSummaryById(userId)).isSameAs(summary);
        assertThat(service.getSummaryByUsername("alice")).isSameAs(summary);
        assertThat(service.findSummaryByEmailOrNull("alice@example.com")).isSameAs(summary);
        assertThat(service.listSummariesByIds(List.of(userId))).containsExactly(summary);
        assertThat(service.getProfile(userId)).isSameAs(profile);

        verify(userQueryService).getSummaryById(userId);
        verify(userQueryService).getSummaryByUsername("alice");
        verify(userQueryService).findSummaryByEmailOrNull("alice@example.com");
        verify(userQueryService).listSummariesByIds(List.of(userId));
        verify(userQueryService).getProfile(userId);
    }

    @Test
    void resolveByUsernameShouldMapSummaryViewToControllerResponse() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);
        UUID userId = uuid(7);
        when(userQueryService.getSummaryByUsername("alice"))
                .thenReturn(new UserSummaryView(userId, "alice", "h7", 2));

        UserResolveResponse response = service.resolveByUsername("alice");

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getHeaderUrl()).isEqualTo("h7");
        verify(userQueryService).getSummaryByUsername("alice");
    }

    @Test
    void requireExistingUserShouldDelegateToLightweightUserLookup() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);
        UUID userId = uuid(7);
        User user = new User();
        user.setId(userId);
        when(userQueryService.getById(userId)).thenReturn(user);

        service.requireExistingUser(userId);

        verify(userQueryService).getById(userId);
        verify(userQueryService, never()).getProfile(userId);
    }

    @Test
    void resolveByUsernameShouldRaiseBusinessExceptionWhenUserMissing() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);
        when(userQueryService.getSummaryByUsername("alice")).thenReturn(null);

        assertThatThrownBy(() -> service.resolveByUsername("alice"))
                .isInstanceOf(BusinessException.class);

        verify(userQueryService).getSummaryByUsername("alice");
    }

    @Test
    void listSummaryResponsesByIdsShouldDeduplicateCapAndPreserveOrder() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);
        UUID aliceId = uuid(7);
        UUID bobId = uuid(9);
        UUID ignoredTail = uuid(11);
        List<UUID> raw = Arrays.asList(aliceId, bobId, aliceId, null, ignoredTail);
        when(userQueryService.listSummariesByIds(List.of(aliceId, bobId, ignoredTail)))
                .thenReturn(Arrays.asList(
                        new UserSummaryView(ignoredTail, "tail", "h11", 3),
                        new UserSummaryView(bobId, "bob", "h9", 2),
                        new UserSummaryView(aliceId, "alice", "h7", 1),
                        new UserSummaryView(null, "skip", "hx", 4),
                        null
                ));

        List<UserSummaryResponse> result = service.listSummaryResponsesByIds(raw);

        assertThat(result).extracting(UserSummaryResponse::getId).containsExactly(aliceId, bobId, ignoredTail);
        assertThat(result).extracting(UserSummaryResponse::getUsername).containsExactly("alice", "bob", "tail");
        verify(userQueryService).listSummariesByIds(List.of(aliceId, bobId, ignoredTail));
    }

    @Test
    void listSummaryResponsesByIdsShouldReturnEmptyForNullOrBlankInputs() {
        UserReadApplicationService service = new UserReadApplicationService(userQueryService);

        assertThat(service.listSummaryResponsesByIds(null)).isEmpty();
        assertThat(service.listSummaryResponsesByIds(List.of())).isEmpty();
        assertThat(service.listSummaryResponsesByIds(Arrays.asList(null, null))).isEmpty();
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
