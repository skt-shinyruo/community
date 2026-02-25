package com.nowcoder.community.social.api.rpc;

// social-service 读取型 RPC 接口：供 user-service 等服务聚合展示使用（获赞/关注/粉丝/关注状态等）。
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.social.api.rpc.dto.UserProfileStats;

public interface SocialReadRpcService {

    Result<Long> userLikeCount(int userId);

    Result<Long> followeeCount(int userId);

    Result<Long> followerCount(int userId);

    Result<Boolean> hasFollowedUser(int actorUserId, int targetUserId);

    Result<UserProfileStats> userProfileStats(int userId, Integer viewerId);
}

