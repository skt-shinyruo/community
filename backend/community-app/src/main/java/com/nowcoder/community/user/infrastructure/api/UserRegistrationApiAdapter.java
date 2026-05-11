package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserRegistrationActionApi;
import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.UserRegistrationApplicationService;
import com.nowcoder.community.user.application.command.CreateVerifiedRegistrationUserCommand;
import com.nowcoder.community.user.application.result.PreparedRegistrationUserResult;
import com.nowcoder.community.user.application.result.UserCredentialResult;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationApiAdapter implements UserRegistrationActionApi {

    private final UserRegistrationApplicationService applicationService;

    public UserRegistrationApiAdapter(UserRegistrationApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email) {
        PreparedRegistrationUserResult result = applicationService.prepareRegistrationUser(username, password, email);
        return new PreparedRegistrationUserView(
                result.userId(),
                result.username(),
                result.email(),
                result.encodedPassword(),
                result.headerUrl()
        );
    }

    @Override
    public UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command) {
        CreateVerifiedRegistrationUserCommand applicationCommand = command == null ? null : new CreateVerifiedRegistrationUserCommand(
                command.userId(),
                command.username(),
                command.encodedPassword(),
                command.email(),
                command.headerUrl()
        );
        return toCredentialView(applicationService.createVerifiedRegistrationUser(applicationCommand));
    }

    private UserCredentialView toCredentialView(UserCredentialResult result) {
        if (result == null) {
            return null;
        }
        return new UserCredentialView(
                result.userId(),
                result.username(),
                result.status(),
                result.type(),
                result.headerUrl()
        );
    }
}
