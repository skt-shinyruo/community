package com.nowcoder.community.user.api.query;

import com.nowcoder.community.user.api.model.UserAuthenticationResultView;
import com.nowcoder.community.user.api.model.UserCredentialView;

import java.util.List;
import java.util.UUID;

public interface UserCredentialQueryApi {

    /**
     * Resolves the database login identity before the expensive password check.
     *
     * <p>The returned challenge is a one-shot, synchronous capability. It keeps
     * credential material inside the user domain while allowing the auth caller
     * to verify that the authenticated identity did not change before BCrypt
     * completes.
     * Unknown users return a challenge with a {@code null} user id and still run
     * the same dummy password check when authenticated.</p>
     */
    AuthenticationChallenge prepareAuthentication(String username);

    /**
     * Returns an opaque, existence-independent subject derived from the same
     * database collation used by username lookup.
     */
    AuthenticationSubject authenticationSubject(String username);

    UserCredentialView getByUserId(UUID userId);

    UserCredentialView findByEmailOrNull(String email);

    List<String> authoritiesOf(UserCredentialView user);

    interface AuthenticationChallenge {

        UUID userId();

        UserAuthenticationResultView authenticate(String password);
    }

    record AuthenticationSubject(String value) {

        public AuthenticationSubject {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("authentication subject must not be blank");
            }
            value = value.trim();
        }
    }
}
