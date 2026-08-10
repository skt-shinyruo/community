package com.nowcoder.community.auth.application;

import com.nowcoder.community.user.api.model.UserCredentialView;
import com.nowcoder.community.user.api.query.UserCredentialQueryApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenFreshnessApplicationService {

    public record TokenFreshnessResult(Status status) {

        public enum Status {
            ACCEPTED,
            STALE,
            DENIED
        }

        public static TokenFreshnessResult accepted() {
            return new TokenFreshnessResult(Status.ACCEPTED);
        }

        public static TokenFreshnessResult stale() {
            return new TokenFreshnessResult(Status.STALE);
        }

        public static TokenFreshnessResult denied() {
            return new TokenFreshnessResult(Status.DENIED);
        }
    }

    private final UserCredentialQueryApi userCredentialQueryApi;

    public TokenFreshnessApplicationService(UserCredentialQueryApi userCredentialQueryApi) {
        this.userCredentialQueryApi = userCredentialQueryApi;
    }

    public TokenFreshnessResult verify(UUID userId, long tokenSecurityVersion) {
        if (userId == null || tokenSecurityVersion <= 0) {
            return TokenFreshnessResult.stale();
        }
        UserCredentialView credential = userCredentialQueryApi.getByUserId(userId);
        if (credential == null || !credential.loginAllowed()) {
            return TokenFreshnessResult.denied();
        }
        if (credential.securityVersion() != tokenSecurityVersion) {
            return TokenFreshnessResult.stale();
        }
        return TokenFreshnessResult.accepted();
    }
}
