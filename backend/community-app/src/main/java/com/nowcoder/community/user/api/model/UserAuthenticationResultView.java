package com.nowcoder.community.user.api.model;

public record UserAuthenticationResultView(
        UserCredentialView user,
        Failure failure
) {

    public enum Failure {
        NONE,
        INVALID_CREDENTIALS,
        USER_DISABLED
    }

    public static UserAuthenticationResultView authenticated(UserCredentialView user) {
        return new UserAuthenticationResultView(user, Failure.NONE);
    }

    public static UserAuthenticationResultView invalidCredentials() {
        return new UserAuthenticationResultView(null, Failure.INVALID_CREDENTIALS);
    }

    public static UserAuthenticationResultView userDisabled(UserCredentialView user) {
        return new UserAuthenticationResultView(user, Failure.USER_DISABLED);
    }

    public boolean authenticated() {
        return failure == Failure.NONE && user != null && user.userId() != null;
    }
}
