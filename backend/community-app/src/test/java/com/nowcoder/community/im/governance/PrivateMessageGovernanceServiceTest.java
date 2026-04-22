package com.nowcoder.community.im.governance;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.im.governance.action.PrivateMessageGovernanceActionApi;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PrivateMessageGovernanceServiceTest {

    private static final UUID USER_ID_1 = uuid(1);
    private static final UUID USER_ID_2 = uuid(2);
    private static final UUID USER_ID_7 = uuid(7);

    private final UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
    private final UserModerationGuard moderationGuard = mock(UserModerationGuard.class);
    private final SocialBlockQueryApi blockQueryApi = mock(SocialBlockQueryApi.class);
    private final PrivateMessageGovernanceService service =
            new PrivateMessageGovernanceService(userLookupQueryApi, moderationGuard, blockQueryApi);

    @Test
    void governanceServiceShouldOnlyExposeUserLookupQueryBasedConstructor() {
        assertThat(PrivateMessageGovernanceService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserLookupQueryApi.class,
                        UserModerationGuard.class,
                        SocialBlockQueryApi.class
                ));
    }

    @Test
    void governanceServiceShouldImplementPrivateMessageGovernanceActionApi() {
        assertThat(PrivateMessageGovernanceActionApi.class).isAssignableFrom(PrivateMessageGovernanceService.class);
    }

    @Test
    void moderationGuardsShouldOnlyExposeUserModerationQueryApiConstructors() {
        assertThat(UserModerationGuard.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class
                ));
        assertThat(com.nowcoder.community.content.service.UserModerationGuard.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        UserModerationQueryApi.class
                ));
    }

    @Test
    void validateShouldRejectInvalidSenderId() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(null, USER_ID_2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("fromUserId 非法");
        verifyNoInteractions(userLookupQueryApi, moderationGuard, blockQueryApi);
    }

    @Test
    void validateShouldRejectInvalidRecipientId() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(USER_ID_1, null),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("toUserId 非法");
        verifyNoInteractions(userLookupQueryApi, moderationGuard, blockQueryApi);
    }

    @Test
    void validateShouldRejectSelfSend() {
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(USER_ID_7, USER_ID_7),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("不能给自己发送私信");
        verifyNoInteractions(userLookupQueryApi, moderationGuard, blockQueryApi);
    }

    @Test
    void validateShouldPropagateMissingSender() {
        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND)).when(userLookupQueryApi).requireSummaryById(USER_ID_1);
        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(USER_ID_1, USER_ID_2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo(UserErrorCode.USER_NOT_FOUND.getMessage());
        verify(moderationGuard, never()).assertCanSendMessage(USER_ID_1);
        verify(userLookupQueryApi, never()).requireSummaryById(USER_ID_2);
        verifyNoInteractions(blockQueryApi);
    }

    @Test
    void validateShouldTranslateMissingReceiver() {
        when(userLookupQueryApi.requireSummaryById(USER_ID_1)).thenReturn(new UserSummaryView(USER_ID_1, "from", "/h1", 0));
        doThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在")).when(userLookupQueryApi).requireSummaryById(USER_ID_2);

        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(USER_ID_1, USER_ID_2),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verify(moderationGuard).assertCanSendMessage(USER_ID_1);
        verifyNoInteractions(blockQueryApi);
    }

    @Test
    void validateShouldRejectModerationDeniedBeforeReceiverLookup() {
        when(userLookupQueryApi.requireSummaryById(USER_ID_1)).thenReturn(new UserSummaryView(USER_ID_1, "from", "/h1", 0));
        BusinessException moderationDenied = new BusinessException(CommonErrorCode.FORBIDDEN, "你已被禁言，暂时无法发送私信");
        doThrow(moderationDenied).when(moderationGuard).assertCanSendMessage(USER_ID_1);

        BusinessException ex = catchThrowableOfType(
                () -> service.validateCanSendPrivateMessage(USER_ID_1, USER_ID_2),
                BusinessException.class
        );

        assertThat(ex).isSameAs(moderationDenied);
        verify(userLookupQueryApi).requireSummaryById(USER_ID_1);
        verify(moderationGuard).assertCanSendMessage(USER_ID_1);
        verify(userLookupQueryApi, never()).requireSummaryById(USER_ID_2);
        verifyNoInteractions(blockQueryApi);
    }

    @Test
    void validateShouldRejectBlockedPair() {
        when(userLookupQueryApi.requireSummaryById(USER_ID_1)).thenReturn(new UserSummaryView(USER_ID_1, "from", "/h1", 0));
        when(userLookupQueryApi.requireSummaryById(USER_ID_2)).thenReturn(new UserSummaryView(USER_ID_2, "to", "/h2", 0));
        when(blockQueryApi.isEitherBlocked(USER_ID_1, USER_ID_2)).thenReturn(true);

        assertThatThrownBy(() -> service.validateCanSendPrivateMessage(USER_ID_1, USER_ID_2))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
                    assertThat(businessException.getMessage()).isEqualTo("双方存在拉黑关系，无法发送私信");
                });
    }

    @Test
    void validateShouldCallCollaboratorsInOrderWhenAllowed() {
        when(userLookupQueryApi.requireSummaryById(USER_ID_1)).thenReturn(new UserSummaryView(USER_ID_1, "from", "/h1", 0));
        when(userLookupQueryApi.requireSummaryById(USER_ID_2)).thenReturn(new UserSummaryView(USER_ID_2, "to", "/h2", 0));

        service.validateCanSendPrivateMessage(USER_ID_1, USER_ID_2);

        InOrder inOrder = inOrder(userLookupQueryApi, moderationGuard, blockQueryApi);
        inOrder.verify(userLookupQueryApi).requireSummaryById(USER_ID_1);
        inOrder.verify(moderationGuard).assertCanSendMessage(USER_ID_1);
        inOrder.verify(userLookupQueryApi).requireSummaryById(USER_ID_2);
        inOrder.verify(blockQueryApi).isEitherBlocked(USER_ID_1, USER_ID_2);
        inOrder.verifyNoMoreInteractions();
    }

}
