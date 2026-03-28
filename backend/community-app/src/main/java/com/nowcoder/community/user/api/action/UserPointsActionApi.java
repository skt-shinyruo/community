package com.nowcoder.community.user.api.action;

public interface UserPointsActionApi {

    boolean applyPoints(int userId, String eventId, String eventType, int delta);
}
