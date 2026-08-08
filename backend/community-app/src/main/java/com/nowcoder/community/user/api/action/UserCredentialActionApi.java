package com.nowcoder.community.user.api.action;

import java.util.UUID;

public interface UserCredentialActionApi {

    void validatePasswordPolicy(String newPassword);

    boolean updatePasswordIfSecurityVersion(
            UUID userId,
            String newPassword,
            long expectedSecurityVersion
    );
}
