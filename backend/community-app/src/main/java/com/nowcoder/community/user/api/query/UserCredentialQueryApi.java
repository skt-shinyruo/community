package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;

import java.util.List;

public interface UserCredentialQueryApi {

    UserAuthenticationResultView authenticate(String username, String password);

    UserCredentialView getByUserId(int userId);

    UserCredentialView findByEmailOrNull(String email);

    List<String> authoritiesOf(UserCredentialView user);
}
