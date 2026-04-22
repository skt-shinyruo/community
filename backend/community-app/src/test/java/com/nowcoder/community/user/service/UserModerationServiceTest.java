package com.nowcoder.community.user.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.entity.User;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final UUID USER_ID_3 = UUID.fromString("00000000-0000-7000-8000-000000000003");
    private static final UUID USER_ID_4 = UUID.fromString("00000000-0000-7000-8000-000000000004");
    private static final UUID USER_ID_7 = UUID.fromString("00000000-0000-7000-8000-000000000007");

    @Mock
    private UserMapper userMapper;

    @Test
    void getModerationStateShouldProjectMuteAndBanTimestamps() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectById(USER_ID_7)).thenReturn(userWithModeration(USER_ID_7));

        UserModerationStateView status = service.getModerationState(USER_ID_7);

        assertThat(status.userId()).isEqualTo(USER_ID_7);
        assertThat(status.muteUntil()).isEqualTo(Instant.parse("2026-03-27T10:15:30Z"));
        assertThat(status.banUntil()).isEqualTo(Instant.parse("2026-03-28T10:15:30Z"));
    }

    @Test
    void scanModerationStatusesAfterIdShouldClampInputsAndSkipInvalidRows() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectModerationUsersAfterId(ZERO_UUID, 500)).thenReturn(Arrays.asList(
                null,
                userWithId(null),
                userWithModeration(USER_ID_3),
                userWithId(USER_ID_4)
        ));

        List<UserModerationService.ModerationStatus> statuses = service.scanModerationStatusesAfterId(null, 999);

        assertThat(statuses)
                .extracting(UserModerationService.ModerationStatus::getUserId)
                .containsExactly(USER_ID_3, USER_ID_4);
        assertThat(statuses.get(0).getMuteUntil()).isEqualTo(Instant.parse("2026-03-27T10:15:30Z"));
        assertThat(statuses.get(1).getMuteUntil()).isNull();
        verify(userMapper).selectModerationUsersAfterId(ZERO_UUID, 500);
    }

    @Test
    void applyModerationShouldMuteUserAndPersistProjectedTimestamp() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectById(USER_ID_7)).thenReturn(userWithId(USER_ID_7));
        when(userMapper.updateModerationUntil(eq(USER_ID_7), any(Date.class), isNull())).thenReturn(1);

        Instant before = Instant.now();
        UserModerationService.ModerationStatus status = service.applyModeration(USER_ID_7, "mute", 120);
        Instant after = Instant.now();

        assertThat(status.getUserId()).isEqualTo(USER_ID_7);
        assertThat(status.getBanUntil()).isNull();
        assertThat(status.getMuteUntil()).isBetween(before.plusSeconds(120), after.plusSeconds(120));
        verify(userMapper).updateModerationUntil(USER_ID_7, Date.from(status.getMuteUntil()), null);
    }

    @Test
    void applyModerationShouldBanUserAndPreserveExistingMute() {
        UserModerationService service = new UserModerationService(userMapper);
        User user = userWithId(USER_ID_7);
        user.setMuteUntil(Date.from(Instant.parse("2026-03-27T10:15:30Z")));
        when(userMapper.selectById(USER_ID_7)).thenReturn(user);
        when(userMapper.updateModerationUntil(eq(USER_ID_7), eq(Date.from(Instant.parse("2026-03-27T10:15:30Z"))), any(Date.class))).thenReturn(1);

        Instant before = Instant.now();
        UserModerationService.ModerationStatus status = service.applyModeration(USER_ID_7, "ban", 300);
        Instant after = Instant.now();

        assertThat(status.getMuteUntil()).isEqualTo(Instant.parse("2026-03-27T10:15:30Z"));
        assertThat(status.getBanUntil()).isBetween(before.plusSeconds(300), after.plusSeconds(300));
        verify(userMapper).updateModerationUntil(USER_ID_7, Date.from(status.getMuteUntil()), Date.from(status.getBanUntil()));
    }

    @Test
    void applyModerationShouldClearMuteWithoutChangingBan() {
        UserModerationService service = new UserModerationService(userMapper);
        User user = userWithModeration(USER_ID_7);
        when(userMapper.selectById(USER_ID_7)).thenReturn(user);
        when(userMapper.updateModerationUntil(USER_ID_7, null, Date.from(Instant.parse("2026-03-28T10:15:30Z")))).thenReturn(1);

        UserModerationService.ModerationStatus status = service.applyModeration(USER_ID_7, "unmute", 300);

        assertThat(status.getMuteUntil()).isNull();
        assertThat(status.getBanUntil()).isEqualTo(Instant.parse("2026-03-28T10:15:30Z"));
        verify(userMapper).updateModerationUntil(USER_ID_7, null, Date.from(status.getBanUntil()));
    }

    @Test
    void applyModerationShouldClearBanWithoutChangingMute() {
        UserModerationService service = new UserModerationService(userMapper);
        User user = userWithModeration(USER_ID_7);
        when(userMapper.selectById(USER_ID_7)).thenReturn(user);
        when(userMapper.updateModerationUntil(USER_ID_7, Date.from(Instant.parse("2026-03-27T10:15:30Z")), null)).thenReturn(1);

        UserModerationService.ModerationStatus status = service.applyModeration(USER_ID_7, "unban", 300);

        assertThat(status.getMuteUntil()).isEqualTo(Instant.parse("2026-03-27T10:15:30Z"));
        assertThat(status.getBanUntil()).isNull();
        verify(userMapper).updateModerationUntil(USER_ID_7, Date.from(status.getMuteUntil()), null);
    }

    @Test
    void applyModerationShouldRejectBlankAction() {
        UserModerationService service = new UserModerationService(userMapper);

        assertThatThrownBy(() -> service.applyModeration(USER_ID_7, " ", 60))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("action 不能为空");
                });
    }

    @Test
    void applyModerationShouldRejectInvalidAction() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectById(USER_ID_7)).thenReturn(userWithId(USER_ID_7));

        assertThatThrownBy(() -> service.applyModeration(USER_ID_7, "freeze", 60))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(INVALID_ARGUMENT);
                    assertThat(businessException.getMessage()).isEqualTo("action 非法");
                });

        verify(userMapper, never()).updateModerationUntil(eq(USER_ID_7), any(), any());
    }

    @Test
    void applyModerationShouldRejectMissingUser() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectById(USER_ID_7)).thenReturn(null);

        assertThatThrownBy(() -> service.applyModeration(USER_ID_7, "mute", 60))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));

        verify(userMapper, never()).updateModerationUntil(eq(USER_ID_7), any(), any());
    }

    @Test
    void applyModerationShouldFailWhenMapperUpdateFails() {
        UserModerationService service = new UserModerationService(userMapper);
        when(userMapper.selectById(USER_ID_7)).thenReturn(userWithId(USER_ID_7));
        when(userMapper.updateModerationUntil(eq(USER_ID_7), any(Date.class), isNull())).thenReturn(0);

        assertThatThrownBy(() -> service.applyModeration(USER_ID_7, "mute", 60))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.INTERNAL_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("更新处罚状态失败");
                });
    }

    private User userWithModeration(UUID id) {
        User user = userWithId(id);
        user.setMuteUntil(Date.from(Instant.parse("2026-03-27T10:15:30Z")));
        user.setBanUntil(Date.from(Instant.parse("2026-03-28T10:15:30Z")));
        return user;
    }

    private User userWithId(UUID id) {
        User user = new User();
        user.setId(id);
        user.setCreateTime(Date.from(Instant.now().minus(Duration.ofMinutes(1))));
        return user;
    }
}
