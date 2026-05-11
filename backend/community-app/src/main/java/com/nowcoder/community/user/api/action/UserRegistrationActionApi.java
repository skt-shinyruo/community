package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.PreparedRegistrationUserView;
import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.model.VerifiedRegistrationUserCommand;

public interface UserRegistrationActionApi {

    PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email);

    UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command);
}
