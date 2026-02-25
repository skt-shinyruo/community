package com.nowcoder.community.social.rpc;

import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.ErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.domain.EntityTypes;
import com.nowcoder.community.contracts.exception.BusinessException;
import com.nowcoder.community.social.api.rpc.SocialReadRpcService;
import com.nowcoder.community.social.api.rpc.dto.UserProfileStats;
import com.nowcoder.community.social.follow.FollowService;
import com.nowcoder.community.social.like.LikeService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

@DubboService
public class SocialReadRpcServiceImpl implements SocialReadRpcService {

    private static final int USER_ENTITY_TYPE = EntityTypes.USER;

    private final LikeService likeService;
    private final FollowService followService;

    public SocialReadRpcServiceImpl(LikeService likeService, FollowService followService) {
        this.likeService = likeService;
        this.followService = followService;
    }

    @Override
    public Result<Long> userLikeCount(int userId) {
        try {
            if (userId <= 0) {
                return Result.ok(0L);
            }
            return Result.ok(likeService.userLikeCount(userId));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Long> followeeCount(int userId) {
        try {
            if (userId <= 0) {
                return Result.ok(0L);
            }
            return Result.ok(followService.followeeCount(userId, USER_ENTITY_TYPE));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Long> followerCount(int userId) {
        try {
            if (userId <= 0) {
                return Result.ok(0L);
            }
            return Result.ok(followService.followerCount(USER_ENTITY_TYPE, userId));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Boolean> hasFollowedUser(int actorUserId, int targetUserId) {
        try {
            if (actorUserId <= 0 || targetUserId <= 0 || actorUserId == targetUserId) {
                return Result.ok(false);
            }
            return Result.ok(followService.hasFollowed(actorUserId, USER_ENTITY_TYPE, targetUserId));
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<UserProfileStats> userProfileStats(int userId, Integer viewerId) {
        try {
            if (userId <= 0) {
                return Result.ok(new UserProfileStats());
            }

            UserProfileStats resp = new UserProfileStats();
            resp.setLikeCount(likeService.userLikeCount(userId));
            resp.setFolloweeCount(followService.followeeCount(userId, USER_ENTITY_TYPE));
            resp.setFollowerCount(followService.followerCount(USER_ENTITY_TYPE, userId));

            boolean hasFollowed = false;
            int v = viewerId == null ? 0 : viewerId;
            if (v > 0 && v != userId) {
                hasFollowed = followService.hasFollowed(v, USER_ENTITY_TYPE, userId);
            }
            resp.setHasFollowed(hasFollowed);

            return Result.ok(resp);
        } catch (BusinessException e) {
            return error(e);
        } catch (RuntimeException e) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> Result<T> error(BusinessException e) {
        if (e == null) {
            return Result.error(CommonErrorCode.INTERNAL_ERROR);
        }
        ErrorCode ec = e.getErrorCode() == null ? CommonErrorCode.INTERNAL_ERROR : e.getErrorCode();
        String msg = StringUtils.hasText(e.getMessage()) ? e.getMessage() : ec.getMessage();
        return Result.error(ec.getCode(), msg);
    }
}

