package com.nowcoder.community.user.application.port;

public interface UsernameAuthenticationSubjectPort {

    String resolve(String validatedUsername);
}
