package com.nowcoder.community.message.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.social.block.BlockService;
import com.nowcoder.community.user.exception.UserErrorCode;
import com.nowcoder.community.user.service.UserModerationService;
import com.nowcoder.community.user.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrivateMessageGovernanceServiceTest {

    private final UserQueryService userQueryService = mock(UserQueryService.class);
    private final UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
    private final BlockService blockService = mock(BlockService.class);
    private final PrivateMessageGovernanceService service =
            new PrivateMessageGovernanceService(userQueryService, moderationGuard, blockService);

    @Test
    void governanceServiceShouldOnlyExposeUserQueryBasedConstructor() {
        assertThat(PrivateMessageGovernanceService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserQueryService.class,
                        UserModerationGuard.class,
                        BlockService.class
                ));
    }

    @Test
    void moderationGuardsShouldOnlyExposeUserModerationServiceConstructors() {
        assertThat(com.nowcoder.community.message.service.UserModerationGuard.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationService.class
                ));
        assertThat(com.nowcoder.community.content.service.UserModerationGuard.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationService.class
                ));
    }

    @Test
    void validateShouldRejectInvalidSenderId() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(0, 2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("fromUserId 非法");
        verifyNoInteractions(userQueryService, moderationGuard, blockService);
    }

    @Test
    void validateShouldRejectInvalidRecipientId() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(1, 0),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("toUserId 非法");
        verifyNoInteractions(userQueryService, moderationGuard, blockService);
    }

    @Test
    void validateShouldRejectSelfSend() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(7, 7),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("不能给自己发送私信");
        verifyNoInteractions(userQueryService, moderationGuard, blockService);
    }

    @Test
    void validateShouldPropagateMissingSender() {
        when(userQueryService.getById(1)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(1, 2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage());
        verify(moderationGuard, never()).assertCanSendMessage(1);
        verify(userQueryService, never()).getById(2);
        verifyNoInteractions(blockService);
    }

    @Test
    void validateShouldTranslateMissingReceiver() {
        when(userQueryService.getById(2)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(1, 2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verify(moderationGuard).assertCanSendMessage(1);
        verifyNoInteractions(blockService);
    }

    @Test
    void validateShouldRejectModerationDeniedBeforeReceiverLookup() {
        BusinessException moderationDenied = new BusinessException(CommonErrorCode.FORBIDDEN, "你已被禁言，暂时无法发送私信");
        doThrow(moderationDenied).when(moderationGuard).assertCanSendMessage(1);

        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(1, 2),
                BusinessException.class
        );

        assertThat(ex).isSameAs(moderationDenied);
        verify(userQueryService).getById(1);
        verify(moderationGuard).assertCanSendMessage(1);
        verify(userQueryService, never()).getById(2);
        verifyNoInteractions(blockService);
    }

    @Test
    void validateShouldRejectBlockedPair() {
        when(blockService.isEitherBlocked(1, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCanSendPrivateMessage(1, 2))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
                    assertThat(businessException.getMessage()).isEqualTo("双方存在拉黑关系，无法发送私信");
                });
    }

    @Test
    void validateShouldCallCollaboratorsInOrderWhenAllowed() {
        service.validateCanSendPrivateMessage(1, 2);

        InOrder inOrder = inOrder(userQueryService, moderationGuard, blockService);
        inOrder.verify(userQueryService).getById(1);
        inOrder.verify(moderationGuard).assertCanSendMessage(1);
        inOrder.verify(userQueryService).getById(2);
        inOrder.verify(blockService).isEitherBlocked(1, 2);
        inOrder.verifyNoMoreInteractions();
    }
}
