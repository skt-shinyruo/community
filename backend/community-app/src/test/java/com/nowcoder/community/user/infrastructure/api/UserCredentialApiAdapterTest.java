package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.application.UserCredentialApplicationService;
import com.nowcoder.community.user.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialApiAdapterTest {

    @Mock
    private UserCredentialApplicationService applicationService;

    @Test
    void getByUserIdShouldReturnNullWhenOwnerReportsMissingUser() {
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000007");
        when(applicationService.getByUserId(userId)).thenThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));
        UserCredentialApiAdapter adapter = new UserCredentialApiAdapter(applicationService);

        UserCredentialView result = adapter.getByUserId(userId);

        assertThat(result).isNull();
    }
}
