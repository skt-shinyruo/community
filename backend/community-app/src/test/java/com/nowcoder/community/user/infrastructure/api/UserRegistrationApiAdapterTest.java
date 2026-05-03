package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.application.UserRegistrationApplicationService;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserRegistrationApiAdapterTest {

    @Test
    void createVerifiedRegistrationUserShouldDelegateNullToApplicationService() {
        UserRegistrationApplicationService applicationService = mock(UserRegistrationApplicationService.class);
        UserRegistrationApiAdapter adapter = new UserRegistrationApiAdapter(applicationService);

        adapter.createVerifiedRegistrationUser(null);

        verify(applicationService).createVerifiedRegistrationUser(isNull(CreateVerifiedRegistrationUserCommand.class));
    }
}
