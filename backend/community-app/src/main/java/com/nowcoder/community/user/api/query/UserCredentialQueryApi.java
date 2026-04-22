package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;

import java.util.List;
import java.util.UUID;

public interface UserCredentialQueryApi {

    UserAuthenticationResultView authenticate(String username, String password);

    UserCredentialView getByUserId(UUID userId);

    UserCredentialView findByEmailOrNull(String email);

    List<String> authoritiesOf(UserCredentialView user);
}
