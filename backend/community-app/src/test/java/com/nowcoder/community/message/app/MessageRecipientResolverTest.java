package com.nowcoder.community.message.app;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.message.dto.SendMessageRequest;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageRecipientResolverTest {

    @Test
    void resolveToUserIdShouldValidateDirectRecipientIdAgainstUserLookupApi() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.requireSummaryById(9)).thenReturn(new UserSummaryView(9, "alice", "/h1", 0));
        MessageRecipientResolver resolver = new MessageRecipientResolver(userLookupQueryApi);
        SendMessageRequest request = new SendMessageRequest();
        request.setToId(9);
        request.setContent("hello");

        int resolved = resolver.resolveToUserId(request);

        assertThat(resolved).isEqualTo(9);
        verify(userLookupQueryApi).requireSummaryById(9);
        verify(userLookupQueryApi, never()).getSummaryByUsername("alice");
    }

    @Test
    void resolveToUserIdShouldTranslateInvalidDirectRecipientIdToUserNotFound() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.requireSummaryById(9)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在"));
        MessageRecipientResolver resolver = new MessageRecipientResolver(userLookupQueryApi);
        SendMessageRequest request = new SendMessageRequest();
        request.setToId(9);
        request.setContent("hello");

        BusinessException ex = catchThrowableOfType(
                () -> resolver.resolveToUserId(request),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verify(userLookupQueryApi).requireSummaryById(9);
        verify(userLookupQueryApi, never()).getSummaryByUsername("alice");
    }

    @Test
    void resolveToUserIdShouldResolveTrimmedToNameWhenToIdMissing() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.requireSummaryByUsername("alice")).thenReturn(new UserSummaryView(9, "alice", "/h1", 0));
        MessageRecipientResolver resolver = new MessageRecipientResolver(userLookupQueryApi);
        SendMessageRequest request = new SendMessageRequest();
        request.setToName("  alice  ");
        request.setContent("hello");

        int resolved = resolver.resolveToUserId(request);

        assertThat(resolved).isEqualTo(9);
        verify(userLookupQueryApi).requireSummaryByUsername("alice");
        verify(userLookupQueryApi, never()).requireSummaryById(9);
    }

    @Test
    void resolveToUserIdShouldReuseSuccessfulTrimmedUsernameResolutionFromCache() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.requireSummaryByUsername("alice")).thenReturn(new UserSummaryView(9, "alice", "/h1", 0));
        MessageRecipientResolver resolver = new MessageRecipientResolver(userLookupQueryApi);

        SendMessageRequest firstRequest = new SendMessageRequest();
        firstRequest.setToName("  alice  ");
        firstRequest.setContent("hello");

        SendMessageRequest secondRequest = new SendMessageRequest();
        secondRequest.setToName("alice");
        secondRequest.setContent("hello again");

        int firstResolved = resolver.resolveToUserId(firstRequest);
        int secondResolved = resolver.resolveToUserId(secondRequest);

        assertThat(firstResolved).isEqualTo(9);
        assertThat(secondResolved).isEqualTo(9);
        verify(userLookupQueryApi, times(1)).requireSummaryByUsername("alice");
        verify(userLookupQueryApi, never()).requireSummaryById(9);
    }

    @Test
    void resolveToUserIdShouldRejectWhenNeitherToIdNorToNameProvided() {
        MessageRecipientResolver resolver = new MessageRecipientResolver(mock(UserLookupQueryApi.class));
        SendMessageRequest request = new SendMessageRequest();
        request.setContent("hello");

        BusinessException ex = catchThrowableOfType(
                () -> resolver.resolveToUserId(request),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_ARGUMENT);
        assertThat(ex.getMessage()).isEqualTo("toId/toName 至少提供一个");
    }

    @Test
    void resolveToUserIdShouldTranslateMissingRecipientToUserNotFound() {
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(userLookupQueryApi.requireSummaryByUsername("missing")).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND, "目标用户不存在"));
        MessageRecipientResolver resolver = new MessageRecipientResolver(userLookupQueryApi);
        SendMessageRequest request = new SendMessageRequest();
        request.setToName("missing");
        request.setContent("hello");

        BusinessException ex = catchThrowableOfType(
                () -> resolver.resolveToUserId(request),
                BusinessException.class
        );

        assertThat(ex).isNotNull();
        assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("目标用户不存在");
        verify(userLookupQueryApi).requireSummaryByUsername("missing");
    }
}
