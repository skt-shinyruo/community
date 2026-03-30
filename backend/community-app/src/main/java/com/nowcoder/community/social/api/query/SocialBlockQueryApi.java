package com.nowcoder.community.social.api.query;

public interface SocialBlockQueryApi {

    boolean isEitherBlocked(int userIdA, int userIdB);
}
