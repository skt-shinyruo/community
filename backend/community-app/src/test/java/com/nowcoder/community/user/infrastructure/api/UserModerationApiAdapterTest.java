package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserModerationActionApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import com.nowcoder.community.user.application.UserModerationApplicationService;
import com.nowcoder.community.user.application.command.ApplyUserModerationCommand;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModerationApiAdapterTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final Instant MUTE_UNTIL = Instant.parse("2026-03-27T10:15:30Z");
    private static final Instant BAN_UNTIL = Instant.parse("2026-03-28T10:15:30Z");

    @Mock
    private UserModerationApplicationService applicationService;

    @Test
    void adapterShouldImplementPublishedModerationApis() {
        UserModerationApiAdapter adapter = new UserModerationApiAdapter(applicationService);

        assertThat(adapter).isInstanceOf(UserModerationActionApi.class);
        assertThat(adapter).isInstanceOf(UserModerationQueryApi.class);
    }

    @Test
    void applyModerationShouldDelegateUsingCommandAndMapView() {
        UserModerationApiAdapter adapter = new UserModerationApiAdapter(applicationService);
        when(applicationService.applyModeration(new ApplyUserModerationCommand(USER_ID, "mute", 60)))
                .thenReturn(new UserModerationStatus(USER_ID, MUTE_UNTIL, BAN_UNTIL));

        UserModerationStateView view = adapter.applyModeration(USER_ID, "mute", 60);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.muteUntil()).isEqualTo(MUTE_UNTIL);
        assertThat(view.banUntil()).isEqualTo(BAN_UNTIL);
        verify(applicationService).applyModeration(new ApplyUserModerationCommand(USER_ID, "mute", 60));
    }

    @Test
    void queryMethodsShouldDelegateAndMapViews() {
        UserModerationApiAdapter adapter = new UserModerationApiAdapter(applicationService);
        when(applicationService.getModerationState(USER_ID))
                .thenReturn(new UserModerationStatus(USER_ID, MUTE_UNTIL, BAN_UNTIL));
        when(applicationService.scanModerationStatesAfterId(USER_ID, 10))
                .thenReturn(List.of(new UserModerationStatus(USER_ID, MUTE_UNTIL, BAN_UNTIL)));

        UserModerationStateView single = adapter.getModerationState(USER_ID);
        List<UserModerationStateView> scanned = adapter.scanModerationStatesAfterId(USER_ID, 10);

        assertThat(single.userId()).isEqualTo(USER_ID);
        assertThat(single.muteUntil()).isEqualTo(MUTE_UNTIL);
        assertThat(single.banUntil()).isEqualTo(BAN_UNTIL);
        assertThat(scanned).hasSize(1);
        assertThat(scanned.get(0).userId()).isEqualTo(USER_ID);
        verify(applicationService).getModerationState(USER_ID);
        verify(applicationService).scanModerationStatesAfterId(USER_ID, 10);
    }
}
