package com.nowcoder.community.social.api.query;

import java.util.UUID;

public interface SocialBlockQueryApi {

    boolean isEitherBlocked(UUID userIdA, UUID userIdB);
}
