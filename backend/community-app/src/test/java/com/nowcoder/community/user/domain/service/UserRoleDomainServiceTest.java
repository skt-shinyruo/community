package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.domain.model.UserAccount;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.FORBIDDEN;
import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserRoleDomainServiceTest {

    private final UserRoleDomainService domainService = new UserRoleDomainService();

    @Test
    void validateCommandShouldRejectNullCommand() {
        Throwable thrown = catchThrowable(() -> domainService.requireValidCommand(false, null, 1, "elevate", true));

        assertBusinessError(thrown, INVALID_ARGUMENT, "request 不能为空");
    }

    @Test
    void validateCommandShouldRequireConfirmTrue() {
        Throwable thrown = catchThrowable(() -> domainService.requireValidCommand(true, uuid(8), 1, "elevate", false));

        assertBusinessError(thrown, INVALID_ARGUMENT, "需要二次确认（confirm=true）");
    }

    @Test
    void validateCommandShouldRequireNonblankReason() {
        Throwable thrown = catchThrowable(() -> domainService.requireValidCommand(true, uuid(8), 1, "   ", true));

        assertBusinessError(thrown, INVALID_ARGUMENT, "reason 不能为空");
    }

    @Test
    void validateCommandShouldRequireTargetUserId() {
        Throwable thrown = catchThrowable(() -> domainService.requireValidCommand(true, null, 1, "elevate", true));

        assertBusinessError(thrown, INVALID_ARGUMENT, "targetUserId 非法");
    }

    @Test
    void validateTargetShouldRejectMissingTargetUser() {
        Throwable thrown = catchThrowable(() -> domainService.requireRoleUpdateAllowed(uuid(99), uuid(8), 1, null));

        assertBusinessError(thrown, INVALID_ARGUMENT, "目标用户不存在");
    }

    @Test
    void validateTargetShouldRejectAdminSelfDowngrade() {
        UUID targetUserId = uuid(8);
        UserAccount target = user(targetUserId, 1);

        Throwable thrown = catchThrowable(() -> domainService.requireRoleUpdateAllowed(targetUserId, targetUserId, 2, target));

        assertBusinessError(thrown, FORBIDDEN, "不允许降级自己的管理员权限");
    }

    @Test
    void normalizeReasonShouldTrimReasonAfterShapeValidation() {
        String reason = domainService.requireValidCommand(true, uuid(8), 1, "  elevate  ", true);

        assertThat(reason).isEqualTo("elevate");
    }

    private static void assertBusinessError(Throwable thrown, Object errorCode, String message) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .hasMessage(message);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(errorCode);
    }

    private static UserAccount user(UUID userId, int type) {
        return new UserAccount(userId, "admin", "pw", "salt", "admin@example.com", type, 0, "h", new Date(), null, null);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
