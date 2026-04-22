package com.nowcoder.community.user.api.action;

import java.util.UUID;

public interface UserCredentialActionApi {

    void updatePassword(UUID userId, String newPassword);
}
