package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserModerationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserModerationDomainServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000007");
    private static final Instant NOW = Instant.parse("2026-04-28T01:00:00Z");
    private static final Instant EXISTING_MUTE = Instant.parse("2026-03-27T10:15:30Z");
    private static final Instant EXISTING_BAN = Instant.parse("2026-03-28T10:15:30Z");

    @Test
    void applyModerationShouldMuteAndPreserveBan() {
        UserModerationDomainService service = new UserModerationDomainService();
        String action = service.requireNonBlankAction("  MUTE  ");

        UserModerationStatus status = service.applyModeration(
                status(EXISTING_MUTE, EXISTING_BAN),
                action,
                120,
                NOW
        );

        assertThat(status.userId()).isEqualTo(USER_ID);
        assertThat(status.muteUntil()).isEqualTo(NOW.plusSeconds(120));
        assertThat(status.banUntil()).isEqualTo(EXISTING_BAN);
    }

    @Test
    void applyModerationShouldBanAndPreserveMute() {
        UserModerationDomainService service = new UserModerationDomainService();

        UserModerationStatus status = service.applyModeration(
                status(EXISTING_MUTE, null),
                "ban",
                300,
                NOW
        );

        assertThat(status.muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(status.banUntil()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void applyModerationShouldClearOnlyRequestedPolicy() {
        UserModerationDomainService service = new UserModerationDomainService();

        UserModerationStatus unmuted = service.applyModeration(
                status(EXISTING_MUTE, EXISTING_BAN),
                "unmute",
                300,
                NOW
        );
        UserModerationStatus unbanned = service.applyModeration(
                status(EXISTING_MUTE, EXISTING_BAN),
                "unban",
                300,
                NOW
        );

        assertThat(unmuted.muteUntil()).isNull();
        assertThat(unmuted.banUntil()).isEqualTo(EXISTING_BAN);
        assertThat(unbanned.muteUntil()).isEqualTo(EXISTING_MUTE);
        assertThat(unbanned.banUntil()).isNull();
    }

    @Test
    void applyModerationShouldClampDurationSeconds() {
        UserModerationDomainService service = new UserModerationDomainService();

        UserModerationStatus negativeDuration = service.applyModeration(
                status(EXISTING_MUTE, null),
                "mute",
                -5,
                NOW
        );
        UserModerationStatus cappedDuration = service.applyModeration(
                status(null, null),
                "ban",
                Integer.MAX_VALUE,
                NOW
        );

        assertThat(negativeDuration.muteUntil()).isNull();
        assertThat(cappedDuration.banUntil()).isEqualTo(NOW.plusSeconds(31_536_000));
    }

    @Test
    void requireNonBlankActionShouldRejectBlankAction() {
        UserModerationDomainService service = new UserModerationDomainService();

        Throwable thrown = catchThrowable(() -> service.requireNonBlankAction(" "));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("action 不能为空");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
    }

    @Test
    void applyModerationShouldRejectUnsupportedAction() {
        UserModerationDomainService service = new UserModerationDomainService();

        Throwable thrown = catchThrowable(() -> service.applyModeration(status(null, null), "freeze", 60, NOW));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage("action 非法");
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(INVALID_ARGUMENT);
    }

    private static UserModerationStatus status(Instant muteUntil, Instant banUntil) {
        return new UserModerationStatus(USER_ID, muteUntil, banUntil);
    }
}
