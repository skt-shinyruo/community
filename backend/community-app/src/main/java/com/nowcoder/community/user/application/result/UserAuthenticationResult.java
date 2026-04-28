package com.nowcoder.community.user.application.result;

public record UserAuthenticationResult(
        UserCredentialResult user,
        Failure failure
) {

    public enum Failure {
        NONE,
        INVALID_CREDENTIALS,
        USER_DISABLED
    }

    public static UserAuthenticationResult authenticated(UserCredentialResult user) {
        return new UserAuthenticationResult(user, Failure.NONE);
    }

    public static UserAuthenticationResult invalidCredentials() {
        return new UserAuthenticationResult(null, Failure.INVALID_CREDENTIALS);
    }

    public static UserAuthenticationResult userDisabled(UserCredentialResult user) {
        return new UserAuthenticationResult(user, Failure.USER_DISABLED);
    }

    public boolean authenticated() {
        return failure == Failure.NONE && user != null && user.userId() != null;
    }
}
