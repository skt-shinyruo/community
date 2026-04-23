package com.nowcoder.community.user.service;

import com.nowcoder.community.user.api.model.UserProfileView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserProfileQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
